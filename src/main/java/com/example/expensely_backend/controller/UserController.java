package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.*;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.*;
import com.example.expensely_backend.utils.JwtUtil;
import com.example.expensely_backend.utils.Mailgun;
import com.example.expensely_backend.utils.S3Service;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
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
	private final RedisSession redisSession;
	private final S3Service s3Service;
	private final OAuthService oAuthService;

	public UserController(UserService userService, JwtUtil jwtUtil,
	                      ExpiredTokenService expiredTokenService, Environment environment, Mailgun mailgun,
	                      EmailOtpService emailOtpService,
	                      DbLogService dbLogService, RedisSession redisSession,
	                      S3Service s3Service, OAuthService oAuthService) {
		this.userService = userService;
		this.jwtUtil = jwtUtil;
		this.expiredTokenService = expiredTokenService;
		this.environment = environment;
		this.mailgun = mailgun;
		this.emailOtpService = emailOtpService;
		this.dbLogService = dbLogService;
		this.redisSession = redisSession;
		this.s3Service = s3Service;
		this.oAuthService = oAuthService;
	}

	private String getCookieValue(HttpServletRequest request, String name) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (cookie.getName().equals(name)) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private String resolveSubjectFromCookies(HttpServletRequest request) {
		String accessToken = getCookieValue(request, "accessToken");
		String subject = null;
		if (accessToken != null) {
			subject = jwtUtil.GetStringFromToken(accessToken);
		}
		if (subject == null) {
			String refreshToken = getCookieValue(request, "refreshToken");
			if (refreshToken != null) {
				subject = jwtUtil.GetStringFromToken(refreshToken);
			}
		}
		return subject;
	}

	private HttpHeaders clearAuthCookies() {
		ResponseCookie clearAccess = ResponseCookie.from("accessToken", "")
				.path("/")
				.maxAge(0)
				.httpOnly(true)
				.secure(true)
				.sameSite("None")
				.build();

		ResponseCookie clearRefresh = ResponseCookie.from("refreshToken", "")
				.path("/")
				.maxAge(0)
				.httpOnly(true)
				.secure(true)
				.sameSite("None")
				.build();

		ResponseCookie clearAdminRefresh = ResponseCookie.from("adminRefreshToken", "")
				.path("/")
				.maxAge(0)
				.httpOnly(true)
				.secure(true)
				.sameSite("None")
				.build();

		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE, clearAccess.toString());
		headers.add(HttpHeaders.SET_COOKIE, clearRefresh.toString());
		headers.add(HttpHeaders.SET_COOKIE, clearAdminRefresh.toString());
		return headers;
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


	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody User user) {
		try {
			user.setEmailVerified(false);
			userService.insertUser(user);
			String otp = emailOtpService.createOrUpdateOtp(user);
			mailgun.sendSimpleMessage(user.getEmail(), "Verify your email",
					"Your OTP is " + otp + ". It expires in 10 minutes.");
			return ResponseEntity.ok(new AuthResponse("Verification OTP sent", user.getId().toString(), ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse(e.getMessage(), null, e.getMessage()));
		}
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(
			HttpServletRequest httprequest,
			@RequestBody User user) {
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

				String refreshToken = null;

				if (httprequest.getCookies() != null) {
					for (Cookie cookie : httprequest.getCookies()) {
						if (cookie.getName().equals("refreshToken")) {
							refreshToken = cookie.getValue();
						}
					}
				}

				if (refreshToken != null && redisSession.isSessionActive(refreshToken)) {
					String subject = jwtUtil.GetStringFromToken(refreshToken);
					if (subject != null) {
						// Generate new access token
						Map<String, String> tokens =
								jwtUtil.GenerateToken(client.getId().toString());


						ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.get("accessToken"))
								.httpOnly(true)
								.secure(true)
								.path("/")
								.sameSite("None")
								.maxAge(15 * 60)  // 15 mins
								.build();
						redisSession.updateLastSeen(refreshToken);
						AuthResponse authResponse = new AuthResponse("User authenticated successfully!"
								, client.getId().toString(), "");
						return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessCookie.toString()).header(
								HttpHeaders.SET_COOKIE).body(authResponse);

					}


				}

				Map<String, String> result =
						jwtUtil.GenerateToken(client.getId().toString());
				if (result == null) {
					return ResponseEntity.status(500).body(new AuthResponse("Token generation failed", user.getId().toString(), "token generation failed"));
				}
				String accessToken = result.get("accessToken");
				refreshToken = result.get("refreshToken");
				String myIP = "";
				if (httprequest.getHeader("X-Forwarded-For") != null) {
					myIP = httprequest.getHeader("X-Forwarded-For").split(",")[0];
				} else {
					myIP = httprequest.getRemoteAddr();
				}
				redisSession.createSession(client.getId().toString(),
						httprequest.getHeader("User-Agent"), refreshToken,
						myIP);

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
	public ResponseEntity<?> getUserById(Authentication authentication, HttpServletRequest request) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body(new UserRes(null, "Unauthorized", false));
		}
		try {
			User user = userService.GetActiveUserById(userId);
			if (user == null) {
				return ResponseEntity.status(404).body(new UserRes(null, "User not found", false));
			}
			boolean isImpersonated = false;
			if (request.getCookies() != null) {
				for (var cookie : request.getCookies()) {
					if (cookie.getName().equals("adminRefreshToken")) {
						isImpersonated = true;
						break;
					}
				}
			}
			return ResponseEntity.ok(new UserRes(user, null, isImpersonated));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage(), false));
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
			Boolean emailUpdated = Boolean.FALSE;
			if (existingUser == null) {
				return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
			}
			// Update fields
			if (user.getEmail() != null && !user.getEmail().equals(existingUser.getEmail())) {
				existingUser.setEmail(user.getEmail());
				existingUser.setEmailVerified(false); // Mark email as unverified if changed
				emailUpdated = Boolean.TRUE;
			}
			if (user.getPhone() != null) existingUser.setPhone(user.getPhone());
			if (user.getCurrency() != null)
				existingUser.setCurrency(user.getCurrency());
			if (user.getName() != null) existingUser.setName(user.getName());
			if (user.getCountry_code() != null)
				existingUser.setCountry_code(user.getCountry_code());
			if (user.isProfileComplete()) existingUser.setProfileComplete(true);
			// Add other fields as necessary

			userService.UpdateUser(existingUser);
			if (emailUpdated) {
				String otp = emailOtpService.createOrUpdateOtp(user);
				mailgun.sendSimpleMessage(user.getEmail(), "Verify your email",
						"Your OTP is " + otp + ". It expires in 10 minutes.");
				return ResponseEntity.ok(new AuthResponse("Verification OTP sent", user.getId().toString(), ""));
			}
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
			if (user.getAlertsEnabled() != null)
				existingUser.setAlertsEnabled(user.getAlertsEnabled());
			if (user.getLanguage() != null)
				existingUser.setLanguage(user.getLanguage());
			if (user.getTheme() != null && !user.getTheme().equals(existingUser.getTheme()))
				existingUser.setTheme(user.getTheme());
			if (user.getThemeColor() != null)
				existingUser.setThemeColor(user.getThemeColor());
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

	@PostMapping("/confirm-password")
	public ResponseEntity<?> confirmPassword(
			Authentication authentication,
			@RequestBody Map<String, String> payload) {
		try {
			String userId = (authentication != null && authentication.getPrincipal() != null)
					? authentication.getPrincipal().toString()
					: (payload != null ? payload.get("userId") : null);
			if (userId == null || userId.isEmpty()) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("valid", false, "message", "User identity required"));
			}
			String password = payload != null ? payload.get("password") : null;
			if (password == null || password.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Password is required"));
			}
			boolean isValid = userService.verifyPassword(userId, password);
			if (isValid) {
				return ResponseEntity.ok(Map.of("valid", true, "message", "Password verified successfully"));
			} else {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("valid", false, "message", "Incorrect password"));
			}
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("valid", false, "message", e.getMessage()));
		}
	}

	@DeleteMapping("/delete-account/{id}")
	public ResponseEntity<?> deleteUser(
			@PathVariable String id) {


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

	@GetMapping("/get-profile-presigned-url")
	public ResponseEntity<?> getProfilePresignedUrl(
			Authentication authentication,
			@RequestParam(value = "fileName", required = false) String fileName,
			@RequestParam(value = "contentType", required = false, defaultValue = "image/jpeg") String contentType
	) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body(new UserRes(null, "Unauthorized"));
		}
		try {
			String ext = "jpg";
			if (fileName != null && fileName.contains(".")) {
				ext = fileName.substring(fileName.lastIndexOf(".") + 1);
			} else if (contentType != null && contentType.contains("/")) {
				ext = contentType.substring(contentType.lastIndexOf("/") + 1);
				if (ext.equals("jpeg")) ext = "jpg";
			}
			String key = "users/" + userId + "/profile_" + System.currentTimeMillis() + "." + ext;
			String presignedUrl = s3Service.generateProfilePresignedURL(key, contentType);
			return ResponseEntity.ok(new S3Resp(presignedUrl, key));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error generating presigned URL: " + e.getMessage()));
		}
	}

	@PatchMapping("/{id}/update-profile-picture")
	public ResponseEntity<?> updateProfilePicture(@PathVariable String id, @RequestParam(value = "filepath", required = false, defaultValue = "") String filePath) {
		try {
			User user = userService.GetActiveUserById(id);
			if (user == null) {
				return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
			}
			String oldFilePath = user.getProfilePicFilePath();
			if (oldFilePath != null && !oldFilePath.isBlank() && !oldFilePath.equals(filePath)) {
				try {
					s3Service.deleteProfileObject(oldFilePath);
				} catch (Exception ex) {
					// Log error deleting old profile picture from S3
				}
			}
			user.setProfilePicFilePath(filePath);
			userService.UpdateUser(user);
			return ResponseEntity.ok(new UserRes(user, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@PostMapping("/verify-oauth-login")
	public ResponseEntity<?> verifyOAuthLogin(HttpServletRequest request,
	                                          @RequestHeader(value = "Authorization", required = false) String authHeader,
	                                          @RequestBody OAuthLoginRequest oAuthRequest) {
		try {
			String provider = oAuthRequest.getProvider() != null ? oAuthRequest.getProvider() : "google";
			String providerUserId = oAuthRequest.getProviderUserId();
			String email = oAuthRequest.getEmail();
			String name = oAuthRequest.getName();

			if (authHeader != null && authHeader.startsWith("Bearer ") && provider.equalsIgnoreCase("google")) {
				String token = authHeader.replace("Bearer ", "");
				try {
					GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
							new NetHttpTransport(),
							JacksonFactory.getDefaultInstance()
					)
							.setAudience(Collections.singletonList(environment.getProperty("GOOGLE_CLIENT_ID")))
							.build();
					GoogleIdToken idToken = verifier.verify(token);
					if (idToken != null) {
						GoogleIdToken.Payload payload = idToken.getPayload();
						if (email == null || email.isBlank()) email = payload.getEmail();
						if (name == null || name.isBlank()) name = (String) payload.get("name");
						if (providerUserId == null || providerUserId.isBlank()) providerUserId = payload.getSubject();
					}
				} catch (Exception ignored) {
					// Fallback to request params
				}
			}

			if ((email == null || email.isBlank()) && (providerUserId == null || providerUserId.isBlank())) {
				return ResponseEntity.badRequest().body(new AuthResponse("Email or provider user ID is required!", null, "email or provider user ID is required"));
			}

			User user = oAuthService.processOAuthLogin(provider, providerUserId, email, name);

			String existingRefreshToken = null;
			if (request.getCookies() != null) {
				for (Cookie cookie : request.getCookies()) {
					if (cookie.getName().equals("refreshToken")) {
						existingRefreshToken = cookie.getValue();
					}
				}
			}

			if (existingRefreshToken != null && redisSession.isSessionActive(existingRefreshToken)) {
				String subject = jwtUtil.GetStringFromToken(existingRefreshToken);
				if (subject != null && subject.equals(user.getId().toString())) {
					Map<String, String> tokens = jwtUtil.GenerateToken(user.getId().toString());
					ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.get("accessToken"))
							.httpOnly(true)
							.secure(true)
							.path("/")
							.sameSite("None")
							.maxAge(15 * 60)
							.build();
					redisSession.updateLastSeen(existingRefreshToken);
					String message = user.isProfileComplete() ? "User authenticated successfully!" : "profile incomplete";
					return ResponseEntity.ok()
							.header(HttpHeaders.SET_COOKIE, accessCookie.toString())
							.body(new AuthResponse(message, user.getId().toString(), ""));
				}
			}

			Map<String, String> result = jwtUtil.GenerateToken(user.getId().toString());
			String accessToken = result.get("accessToken");
			String refreshToken = result.get("refreshToken");
			ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
					.httpOnly(true)
					.secure(true)
					.path("/")
					.sameSite("None")
					.maxAge(15 * 60)
					.build();

			ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
					.httpOnly(true)
					.secure(true)
					.path("/")
					.sameSite("None")
					.maxAge(7 * 24 * 60 * 60)
					.build();

			String ip = "";
			if (request.getHeader("X-Forwarded-For") != null) {
				ip = request.getHeader("X-Forwarded-For").split(",")[0];
			} else {
				ip = request.getRemoteAddr();
			}
			redisSession.createSession(user.getId().toString(), request.getHeader("User-Agent"), refreshToken, ip);

			String message = user.isProfileComplete() ? "User authenticated successfully!" : "profile incomplete";
			return ResponseEntity.ok()
					.header(HttpHeaders.SET_COOKIE, accessCookie.toString())
					.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
					.body(new AuthResponse(message, user.getId().toString(), ""));

		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("OAuth authentication failed: " + e.getMessage(), null, e.getMessage()));
		}
	}

	@GetMapping("/oauth/linked-accounts")
	public ResponseEntity<?> getLinkedOAuthAccounts(Authentication authentication) {
		String userIdStr = authentication != null ? (String) authentication.getPrincipal() : null;
		if (userIdStr == null) {
			return ResponseEntity.status(401).body(new AuthResponse("Unauthorized", null, "Unauthorized"));
		}
		try {
			java.util.UUID userId = java.util.UUID.fromString(userIdStr);
			return ResponseEntity.ok(oAuthService.getLinkedAccounts(userId));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error fetching linked accounts: " + e.getMessage(), null, e.getMessage()));
		}
	}

	@PostMapping("/oauth/link")
	public ResponseEntity<?> linkOAuthAccount(Authentication authentication, @RequestBody OAuthLinkRequest linkRequest) {
		String userIdStr = authentication != null ? (String) authentication.getPrincipal() : null;
		if (userIdStr == null) {
			return ResponseEntity.status(401).body(new AuthResponse("Unauthorized", null, "Unauthorized"));
		}
		try {
			java.util.UUID userId = java.util.UUID.fromString(userIdStr);
			OAuthAccountDto linked = oAuthService.linkAccount(userId, linkRequest.getProvider(), linkRequest.getProviderUserId(), linkRequest.getProviderEmail());
			return ResponseEntity.ok(linked);
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthResponse(e.getMessage(), null, e.getMessage()));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse(e.getMessage(), null, e.getMessage()));
		}
	}

	@DeleteMapping("/oauth/unlink/{provider}")
	public ResponseEntity<?> unlinkOAuthAccount(Authentication authentication, @PathVariable String provider) {
		String userIdStr = authentication != null ? (String) authentication.getPrincipal() : null;
		if (userIdStr == null) {
			return ResponseEntity.status(401).body(new AuthResponse("Unauthorized", null, "Unauthorized"));
		}
		try {
			java.util.UUID userId = java.util.UUID.fromString(userIdStr);
			oAuthService.unlinkAccount(userId, provider);
			return ResponseEntity.ok(new AuthResponse("Successfully unlinked " + provider + " account", userIdStr, ""));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthResponse(e.getMessage(), null, e.getMessage()));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse(e.getMessage(), null, e.getMessage()));
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

		if (!redisSession.isSessionActive(refreshToken)) {
			return ResponseEntity.status(401).body("Session revoked");
		}

		User user = resolveUserFromSubject(subject);
		if (user == null) {
			return ResponseEntity.status(401).body("Invalid refresh token");
		}
		if (!user.isEmailVerified()) {
			return ResponseEntity.status(403).body("email not verified");
		}

		// Generate new access token
		Map<String, String> tokens =
				jwtUtil.GenerateToken(user.getId().toString());


		ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.get("accessToken"))
				.httpOnly(true)
				.secure(true)
				.path("/")
				.sameSite("None")
				.maxAge(15 * 60)  // 15 mins
				.build();

		redisSession.updateLastSeen(refreshToken);
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessCookie.toString()).body(new AuthResponse("Token refreshed successfully!", null, ""));
	}

	@GetMapping("/logout")
	public ResponseEntity<?> logout(HttpServletRequest request) {
		try {
			String subject = resolveSubjectFromCookies(request);
			if (subject != null) {
				redisSession.revokeSession(subject, getCookieValue(request, "refreshToken"));
			}
			return ResponseEntity.noContent().headers(clearAuthCookies()).build();
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
			// Respect user's alerts setting: if alerts are disabled, return empty list
			User user = userService.GetUserById(userId);
			if (user.getAlertsEnabled() != null && !user.getAlertsEnabled()) {
				return ResponseEntity.ok(Collections.emptyList());
			}
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

	@DeleteMapping("/sessions/current")
	public ResponseEntity<?> revokeCurrentSession(Authentication authentication, HttpServletRequest request) {
		String userId = authentication != null ? (String) authentication.getPrincipal() : null;
		if (userId == null) {
			return ResponseEntity.status(401).body(new AuthResponse("Unauthorized", null, "Unauthorized"));
		}
		try {
			redisSession.revokeSession(userId, getCookieValue(request, "refreshToken"));
			return ResponseEntity.ok().headers(clearAuthCookies())
					.body(new AuthResponse("Session revoked", userId, ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error revoking session", null, e.getMessage()));
		}
	}

	@GetMapping("/sessions/get-all")
	public ResponseEntity<?> getAllSessions(Authentication authentication,
	                                        HttpServletRequest request) {
		String userId = (String) authentication.getPrincipal();
		try {


			return ResponseEntity.ok(redisSession.fetchAllSessionsForUser(userId, getCookieValue(request, "refreshToken")));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error " +
					"fetching users session", userId, e.getMessage()));
		}
	}

	@DeleteMapping("/sessions/id/{id}")
	public ResponseEntity<?> deleteOtherSessionForUser(@PathVariable String id,
	                                                   Authentication authentication
	) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body(new AuthResponse("Unauthorized", null, "Unauthorized"));
		}
		try {
			redisSession.revokeSession(userId, id);
			return ResponseEntity.ok()
					.body(new AuthResponse("Session revoked", userId, ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error revoking session", null, e.getMessage()));
		}
	}

	@GetMapping("/check/phone/{number}")
	public ResponseEntity<?> checkPhone(@PathVariable String number) {
		try {
			boolean exists = userService.isUserPresent(null, number);
			return ResponseEntity.ok(Boolean.toString(exists));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error checking phone existence", null, e.getMessage()));
		}
	}

	@GetMapping("/check/email/{email}")
	public ResponseEntity<?> checkEmail(@PathVariable String email) {
		try {
			boolean exists = userService.isUserPresent(email, null);
			return ResponseEntity.ok(Boolean.toString(exists));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error " +
					"checking email existence", null, e.getMessage()));
		}
	}


}
