package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.*;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.DbLogService;
import com.example.expensely_backend.service.EmailOtpService;
import com.example.expensely_backend.service.ExpiredTokenService;
import com.example.expensely_backend.service.UserService;
import com.example.expensely_backend.utils.JwtUtil;
import com.example.expensely_backend.utils.Mailgun;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
	private final UserService userService;
	private final JwtUtil jwtUtil;
	private final ExpiredTokenService expiredTokenService;
	private final Environment environment;
	private final Mailgun mailgun;
	private final EmailOtpService emailOtpService;
	private final DbLogService dbLogService;

	public UserController(UserService userService, JwtUtil jwtUtil,
	                      ExpiredTokenService expiredTokenService, Environment environment, Mailgun mailgun,
	                      EmailOtpService emailOtpService, DbLogService dbLogService) {
		this.userService = userService;
		this.jwtUtil = jwtUtil;
		this.expiredTokenService = expiredTokenService;
		this.environment = environment;
		this.mailgun = mailgun;
		this.emailOtpService = emailOtpService;
		this.dbLogService = dbLogService;
	}


	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody User user) {
		try {
			user.setProfileComplete(true);
			user.setEmailVerified(false);
			userService.save(user);
			String otp = emailOtpService.createOrUpdateOtp(user);
			mailgun.sendSimpleMessage(user.getEmail(), "Verify your email",
					"Your OTP is " + otp + ". It expires in 10 minutes.");
			return ResponseEntity.ok(new AuthResponse("Verification OTP sent", user.getId().toString(), ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse(e.getMessage(), null, e.getMessage()));
		}
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody User user) {
		if (user.getEmail() == null && user.getPhone() == null) {
			{
				return ResponseEntity.badRequest().body(new AuthResponse("Email or Phone is required!", user.getId().toString(), "email or phone is required"));
			}

		}

		if (user.getPassword() == null) {
			return ResponseEntity.badRequest().body(new AuthResponse("Password is required!", user.getId().toString(), "password is required"));
		}

		try {
			if (userService.authenticate(user.getEmail(), user.getPhone(), user.getPassword())) {
				User client = userService.GetUserByEmailOrPhone(user.getEmail(), user.getPhone());
				if (!client.isEmailVerified()) {
					return ResponseEntity.status(403).body(new AuthResponse(
							"email not verified", client.getId().toString(), "email not verified"));
				}

				Map<String, String> result = jwtUtil.GenerateToken(client.getId().toString());
				if (result == null) {
					return ResponseEntity.status(500).body(new AuthResponse("Token generation failed", user.getId().toString(), "token generation failed"));
				}
				String accessToken = result.get("accessToken");
				String refreshToken = result.get("refreshToken");

				ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
						.httpOnly(true)
						.secure(true)
						.path("/")
						.sameSite("None")
						.maxAge(15 * 60)  // 15 mins
						.build();

				ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
						.httpOnly(true)
						.secure(true)
						.path("/")
						.sameSite("None")
						.maxAge(7 * 24 * 60 * 60) // 7 days
						.build();

				User authenticatedUser = userService.GetUserByEmailOrPhone(user.getEmail(), user.getPhone());
				AuthResponse authResponse = new AuthResponse("User authenticated successfully!"
						, authenticatedUser.getId().toString(), "");
				return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessCookie.toString()).header(
						HttpHeaders.SET_COOKIE, refreshCookie.toString()).body(authResponse);
			} else {
				return ResponseEntity.status(401).body(new AuthResponse(
						"Invalid credentials", user.getId() != null ? user.getId().toString() : null,
						"Invalid credentials"));
			}
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse(
					"Something went wrong!", user.getId() != null ? user.getId().toString() : null,
					e.getMessage()));
		}
	}

	@PostMapping("/verify-otp")
	public ResponseEntity<?> verifyOtp(@RequestBody OtpVerifyRequest request) {
		emailOtpService.verifyOtp(request.getUserId(), request.getOtp());
		return ResponseEntity.ok(new AuthResponse("Email verified", request.getUserId(), ""));
	}

	@PostMapping("/resend-otp")
	public ResponseEntity<?> resendOtp(@RequestBody OtpResendRequest request) {
		String otp = emailOtpService.resendOtp(request.getUserId());
		User user = userService.GetUserById(request.getUserId());
		mailgun.sendSimpleMessage(user.getEmail(), "Verify your email",
				"Your OTP is " + otp + ". It expires in 10 minutes.");
		return ResponseEntity.ok(new AuthResponse("Verification OTP resent", request.getUserId(), ""));
	}

	@PostMapping("/request-password-reset")
	public ResponseEntity<?> requestPasswordReset(@RequestBody PasswordResetRequest request) {
		if (request.getEmail() == null || request.getEmail().isBlank()) {
			return ResponseEntity.badRequest().body(new AuthResponse("email is required", null, "email is required"));
		}

		emailOtpService.createPasswordResetToken(request.getEmail()).ifPresent(details -> {
			String frontendUrl = environment.getProperty("FRONTEND_URL");
			if (frontendUrl == null || frontendUrl.isBlank()) {
				throw new IllegalStateException("FRONTEND_URL is not configured");
			}
			String resetLink = frontendUrl + "/reset-password?uid=" + details.getUserId() + "&otp=" + details.getOtpHash();
			dbLogService.logMessage("controller", getClass().getName(), "requestPasswordReset",
					"Generated password reset link: " + resetLink + " " + details.getOtpHash());
			mailgun.sendSimpleMessage(request.getEmail(), "Reset your password",
					"Click the link to reset your password: " + resetLink + "\nThis link expires in 10 minutes.");
		});

		return ResponseEntity.ok(new AuthResponse(
				"If the account exists, a reset link has been sent", null, ""));
	}

	@PostMapping("/confirm-password-reset")
	public ResponseEntity<?> confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
		if (request.getUserId() == null || request.getUserId().isBlank()) {
			return ResponseEntity.badRequest().body(new AuthResponse("userId is required", null, "userId is required"));
		}
		if (request.getOtp() == null || request.getOtp().isBlank()) {
			return ResponseEntity.badRequest().body(new AuthResponse("otp is required", null, "otp is required"));
		}
		if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
			return ResponseEntity.badRequest().body(new AuthResponse("password is required", null, "password is required"));
		}

		User user = emailOtpService.validatePasswordResetToken(request.getUserId(), request.getOtp());
		user.setPassword(request.getNewPassword());
		userService.updatePassword(user);
		return ResponseEntity.ok(new AuthResponse("Password updated", request.getUserId(), ""));
	}

	@GetMapping("/check-auth")
	public ResponseEntity<AuthResponse> validateToken(HttpServletRequest request) {
		try {
			// Extract token from "Bearer <token>"
			String accessToken = null;

			if (request.getCookies() != null) {
				for (Cookie cookie : request.getCookies()) {
					if (cookie.getName().equals("accessToken")) {
						accessToken = cookie.getValue();
					}
				}
			}

			if (accessToken == null) {
				return ResponseEntity.status(401).body(new AuthResponse("Refresh token missing", null, "Refresh token missing"));
			}

			if (expiredTokenService.isTokenExpired(accessToken)) {
				return ResponseEntity.status(401).body(new AuthResponse(null, null, "Token has expired"));
			}

			if (jwtUtil.ValidateToken(accessToken)) {
				return ResponseEntity.ok(new AuthResponse("Token is valid", null, ""));
			} else {
				return ResponseEntity.status(401).body(new AuthResponse("invalid token!", null, "Invalid token"));
			}
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(
					new AuthResponse("Something went wrong!", null, "Error validating token: " + e.getMessage())
			);
		}
	}


	@GetMapping("/me")
	public ResponseEntity<?> getUserById(Authentication authentication) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body(new UserRes(null, "Unauthorized"));
		}
		try {
			User user = userService.GetActiveUserById(userId);
			if (user == null) {
				return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
			}
			return ResponseEntity.ok(new UserRes(user, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@PatchMapping("/update-profile")
	public ResponseEntity<?> updateProfile(Authentication authentication,
	                                       @RequestBody User user) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body(new UserRes(null, "Unauthorized"));
		}
		try {
			User existingUser = userService.GetActiveUserById(userId);
			if (existingUser == null) {
				return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
			}
			// Update fields
			if (user.getEmail() != null) existingUser.setEmail(user.getEmail());
			if (user.getPhone() != null) existingUser.setPhone(user.getPhone());
			if (user.getCurrency() != null)
				existingUser.setCurrency(user.getCurrency());
			if (user.getName() != null) existingUser.setName(user.getName());
			if (user.getCountry_code() != null)
				existingUser.setCountry_code(user.getCountry_code());
			if (user.isProfileComplete()) existingUser.setProfileComplete(true);
			// Add other fields as necessary

			userService.UpdateUser(existingUser);
			return ResponseEntity.ok(new UserRes(existingUser, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@PatchMapping("/update-settings")
	public ResponseEntity<?> updateSettings(@RequestBody User user) {
		try {
			User existingUser = userService.GetActiveUserById(user.getId().toString());
			if (existingUser == null) {
				return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
			}
			if (user.getNotificationsEnabled() != null)
				existingUser.setNotificationsEnabled(user.getNotificationsEnabled());
			if (user.getLanguage() != null)
				existingUser.setLanguage(user.getLanguage());
			if (user.getTheme() != null) existingUser.setTheme(user.getTheme());
			if (user.getCurrency() != null)
				existingUser.setCurrency(user.getCurrency());
			if (user.getIsActive() != null)
				existingUser.setIsActive(user.getIsActive());
			if (user.getIsAdmin() != null)
				existingUser.setIsAdmin(user.getIsAdmin());

			userService.UpdateUser(existingUser);
			return ResponseEntity.ok(new UserRes(existingUser, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@PatchMapping("/update-password")
	public ResponseEntity<?> updatePassword(@RequestBody User user) {
		try {
			User existingUser = userService.GetActiveUserById(user.getId().toString());
			if (existingUser == null) {
				return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
			}
			if (user.getPassword() == null || user.getPassword().isEmpty()) {
				return ResponseEntity.badRequest().body(new UserRes(null, "Password is required"));
			}
			existingUser.setPassword(user.getPassword());
			userService.updatePassword(existingUser);
			return ResponseEntity.ok(new UserRes(existingUser, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@DeleteMapping("/delete-account/{id}")
	public ResponseEntity<?> deleteUser(@PathVariable String id) {
		try {
			User user = userService.GetUserById(id);
			if (user == null) {
				return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
			}
			user.setIsActive(false);
			userService.deleteUser(user);
			return ResponseEntity.ok(new UserRes(null, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@PatchMapping("/{id}/update-profile-picture")
	public ResponseEntity<?> updateProfilePicture(@PathVariable String id, @RequestParam("filepath") String filePath) {
		try {
			User user = userService.GetActiveUserById(id);
			if (user == null) {
				return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
			}
			user.setProfilePicFilePath(filePath);
			userService.UpdateUser(user);
			return ResponseEntity.ok(new UserRes(user, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@PostMapping("/verify-oauth-login")
	public ResponseEntity<?> verifyOAuthLogin(@RequestHeader("Authorization") String authHeader, @RequestBody User user) {
		String token = authHeader.replace("Bearer ", "");
		try {
			GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
					new NetHttpTransport(),
					JacksonFactory.getDefaultInstance()
			)
					.setAudience(Collections.singletonList(environment.getProperty(
							"GOOGLE_CLIENT_ID"
					)))
					.build();

			GoogleIdToken idToken = verifier.verify(token);
			if (idToken != null) {
				GoogleIdToken.Payload payload = idToken.getPayload();
				try {
					if (user.getEmail() == null && user.getPhone() == null) {
						return ResponseEntity.badRequest().body(new AuthResponse("Email or Phone is required!", null, "email or phone is required"));
					}


					if (userService.isUserPresent(user.getEmail(), user.getPhone())) {
						User existingUser = userService.GetUserByEmailOrPhone(user.getEmail(), user.getPhone());
						if (!existingUser.isEmailVerified()) {
							existingUser.setEmailVerified(true);
							userService.UpdateUser(existingUser);
						}
						user.setOauth2User(true);
						Map<String, String> result = jwtUtil.GenerateToken(existingUser.getId().toString());
						String accessToken = result.get("accessToken");
						String refreshToken = result.get("refreshToken");
						ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
								.httpOnly(true)
								.secure(true)
								.path("/")
								.sameSite("None")
								.maxAge(15 * 60)  // 15 mins
								.build();

						ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
								.httpOnly(true)
								.secure(true)
								.path("/")
								.sameSite("None")
								.maxAge(7 * 24 * 60 * 60) // 7 days
								.build();
						return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessCookie.toString()).header(
								HttpHeaders.SET_COOKIE, refreshCookie.toString()).body(new AuthResponse("User authenticated " +
								"successfully!", existingUser.getId().toString(), ""));
					} else {
						// Register new user
						user.setOauth2User(true);
						user.setEmailVerified(true);
						userService.save(user);
						Map<String, String> result = jwtUtil.GenerateToken(user.getEmail());
						String accessToken = result.get("accessToken");
						String refreshToken = result.get("refreshToken");
						ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
								.httpOnly(true)
								.secure(true)
								.path("/")
								.sameSite("None")
								.maxAge(15 * 60)  // 15 mins
								.build();

						ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
								.httpOnly(true)
								.secure(true)
								.path("/")
								.sameSite("None")
								.maxAge(7 * 24 * 60 * 60) // 7 days
								.build();
						return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessCookie.toString()).header(
								HttpHeaders.SET_COOKIE, refreshCookie.toString()).body(new AuthResponse("profile incomplete",
								user.getId().toString(), ""));
					}
				} catch (Exception e) {
					return ResponseEntity.badRequest().body(new AuthResponse("Something went wrong!", null, e.getMessage()));
				}
			} else {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("Invalid ID token", null, "Invalid ID token"));
			}

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("Error verifying token:", null, "Error verifying token"));
		}

	}

	@GetMapping("/all")
	public ResponseEntity<?> getAllUsers(Authentication authentication) {
		String userId = authentication != null ? (String) authentication.getPrincipal() : null;
		if (userId == null) {
			return ResponseEntity.status(401).body(new UserRes(null, "Unauthorized"));
		}
		try {
			User requester = userService.GetUserById(userId);
			if (requester.getIsAdmin() == null || !requester.getIsAdmin()) {
				return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
			}
			return ResponseEntity.ok(userService.getAllUsers());
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@GetMapping("/refresh")
	public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
		String refreshToken = null;

		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (cookie.getName().equals("refreshToken")) {
					refreshToken = cookie.getValue();
				}
			}
		}

		if (refreshToken == null) {
			return ResponseEntity.status(401).body("Refresh token missing");
		}

		String subject = jwtUtil.GetStringFromToken(refreshToken);
		if (subject == null) {
			return ResponseEntity.status(401).body("Invalid refresh token");
		}

		User user = resolveUserFromSubject(subject);
		if (user == null) {
			return ResponseEntity.status(401).body("Invalid refresh token");
		}
		if (!user.isEmailVerified()) {
			return ResponseEntity.status(403).body("email not verified");
		}

		// Generate new access token
		Map<String, String> tokens = jwtUtil.GenerateToken(user.getId().toString());


		ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.get("accessToken"))
				.httpOnly(true)
				.secure(true)
				.path("/")
				.sameSite("None")
				.maxAge(15 * 60)  // 15 mins
				.build();


		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessCookie.toString()).body(new AuthResponse("Token refreshed successfully!", null, ""));
	}

	private User resolveUserFromSubject(String subject) {
		try {
			return userService.GetUserById(subject);
		} catch (Exception e) {
			try {
				return userService.GetUserByEmail(subject);
			} catch (Exception ignored) {
				return null;
			}
		}
	}

	@GetMapping("/logout")
	public ResponseEntity<?> logout(HttpServletResponse response) {
		try {

			ResponseCookie clearAccess = ResponseCookie.from("accessToken", "")
					.path("/")
					.maxAge(0)
					.httpOnly(true)
					.secure(true)
					.sameSite("None") // can be "Strict", "Lax", or "None"
					.build();


			ResponseCookie clearRefresh = ResponseCookie.from("refreshToken", "")
					.path("/")
					.maxAge(0)
					.httpOnly(true)
					.secure(true)
					.sameSite("None")
					.build();

			response.addHeader("Set-Cookie", clearAccess.toString());
			response.addHeader("Set-Cookie", clearRefresh.toString());

			return ResponseEntity.ok("User logged out successfully!");

		} catch (Exception e) {
			return ResponseEntity.badRequest().body(
					new AuthResponse("Error logging out: " + e.getMessage(), null, "internal server error")
			);
		}
	}

	@GetMapping("/alerts")
	public ResponseEntity<?> FetchUserAlerts(Authentication authentication) {

		String userId = (String) authentication.getPrincipal();
		try {
			return ResponseEntity.ok(userService.fetchAllAlertsForUser(userId));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error fetching alerts: " + e.getMessage(), null, "internal server error"));
		}

	}

	@GetMapping("/send-mail-test")
	public ResponseEntity<?> sendMailTest(@RequestParam(name = "to") String to,
	                                      @RequestParam(required = false, name = "subject") String subject, @RequestParam(name = "text") String text) {
		try {
			mailgun.sendSimpleMessage(to, subject, text);
			return ResponseEntity.ok("Mail sent successfully!");
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error sending mail: " + e.getMessage(), null, "internal server error"));
		}
	}

}
