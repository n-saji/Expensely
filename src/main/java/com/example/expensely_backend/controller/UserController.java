package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.AuthResponse;
import com.example.expensely_backend.dto.UserRes;
import com.example.expensely_backend.model.User;
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
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;
import java.time.ZoneId;
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

    public UserController(UserService userService, JwtUtil jwtUtil,
                          ExpiredTokenService expiredTokenService, Environment environment, Mailgun mailgun) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.expiredTokenService = expiredTokenService;
        this.environment = environment;
        this.mailgun = mailgun;
    }


    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            user.setProfileComplete(true);
            userService.save(user);
            return ResponseEntity.ok(new AuthResponse("User registered successfully!", user.getId().toString(), ""));
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
                return ResponseEntity.status(401).body(new AuthResponse("Invalid credentials", null,
                        "Invalid credentials"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse("Something went wrong!", null, e.getMessage()));
        }
    }

    @GetMapping("/check-auth")
    public ResponseEntity<AuthResponse> validateToken(HttpServletRequest request) {
        try {
            // Extract token from "Bearer <token>"
            String refreshToken = null;

            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if (cookie.getName().equals("refreshToken")) {
                        refreshToken = cookie.getValue();
                    }
                }
            }

            if (refreshToken == null) {
                return ResponseEntity.status(401).body(new AuthResponse("Refresh token missing", null, "Refresh token missing"));
            }

            if (expiredTokenService.isTokenExpired(refreshToken)) {
                return ResponseEntity.status(401).body(new AuthResponse(null, null, "Token has expired"));
            }

            if (jwtUtil.ValidateToken(refreshToken)) {
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


    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        try {
            User user = userService.GetActiveUserById(id);
            if (user == null) {
                return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
            }
            return ResponseEntity.ok(new UserRes(user, null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
        }
    }

    @PatchMapping("/update-profile")
    public ResponseEntity<?> updateProfile(@RequestBody User user) {
        try {
            User existingUser = userService.GetActiveUserById(user.getId().toString());
            if (existingUser == null) {
                return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
            }
            // Update fields
            if (user.getEmail() != null) existingUser.setEmail(user.getEmail());
            if (user.getPhone() != null) existingUser.setPhone(user.getPhone());
            if (user.getCurrency() != null) existingUser.setCurrency(user.getCurrency());
            if (user.getName() != null) existingUser.setName(user.getName());
            if (user.getCountry_code() != null)
                existingUser.setCountry_code(user.getCountry_code());
            if (user.isProfileComplete()) existingUser.setProfileComplete(true);
            if (user.getTimeZone() != null && !user.getTimeZone().isBlank()) {
                ResponseEntity<?> tzError = applyTimeZone(user.getTimeZone(), existingUser);
                if (tzError != null) return tzError;
            }
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
            if (user.getLanguage() != null) existingUser.setLanguage(user.getLanguage());
            if (user.getTheme() != null) existingUser.setTheme(user.getTheme());
            if (user.getCurrency() != null) existingUser.setCurrency(user.getCurrency());
            if (user.getIsActive() != null) existingUser.setIsActive(user.getIsActive());
            if (user.getIsAdmin() != null) existingUser.setIsAdmin(user.getIsAdmin());
            if (user.getTimeZone() != null && !user.getTimeZone().isBlank()) {
                ResponseEntity<?> tzError = applyTimeZone(user.getTimeZone(), existingUser);
                if (tzError != null) return tzError;
            }

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
    public ResponseEntity<?> getAllUsers() {
        try {
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

        String email = jwtUtil.GetStringFromToken(refreshToken);
        if (email == null) {
            return ResponseEntity.status(401).body("Invalid refresh token");
        }

        // Generate new access token
        Map<String, String> tokens = jwtUtil.GenerateToken(email);


        ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.get("accessToken"))
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(15 * 60)  // 15 mins
                .build();


        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessCookie.toString()).body(new AuthResponse("Token refreshed successfully!", null, ""));
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
    public ResponseEntity<?> FetchUserAlerts(HttpServletRequest request, HttpServletResponse response) {

        Cookie[] cookies = request.getCookies();
        String refreshToken = null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals("refreshToken")) {
                refreshToken = cookie.getValue();
            }
        }

        if (refreshToken == null) {
            return ResponseEntity.status(401).body("Refresh token missing");
        }
        String userId = jwtUtil.GetStringFromToken(refreshToken);
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

    private ResponseEntity<?> applyTimeZone(String timeZone, User user) {
        try {
            ZoneId.of(timeZone);
            user.setTimeZone(timeZone);
            return null;
        } catch (DateTimeException e) {
            return ResponseEntity.badRequest().body(new UserRes(null, "Invalid timezone: " + timeZone));
        }
    }

}
