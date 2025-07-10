package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.AuthResponse;
import com.example.expensely_backend.dto.UserRes;
import com.example.expensely_backend.model.ExpiredToken;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.ExpiredTokenService;
import com.example.expensely_backend.service.UserService;
import com.example.expensely_backend.utils.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final ExpiredTokenService expiredTokenService;
    private final Environment environment;

    public UserController(UserService userService, JwtUtil jwtUtil, ExpiredTokenService expiredTokenService, Environment environment) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.expiredTokenService = expiredTokenService;
        this.environment = environment;
    }


    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            user.setProfileComplete(true);
            userService.save(user);
            return ResponseEntity.ok(new AuthResponse("User registered successfully!", null, user.getId().toString(), ""));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse(e.getMessage(), null, null, e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        if (user.getEmail() == null && user.getPhone() == null) {
            {
                return ResponseEntity.badRequest().body(new AuthResponse("Email or Phone is required!", null, user.getId().toString(), "email or phone is required"));
            }

        }

        if (user.getPassword() == null) {
            return ResponseEntity.badRequest().body(new AuthResponse("Password is required!", null, user.getId().toString(), "password is required"));
        }

        try {
            if (userService.authenticate(user.getEmail(), user.getPhone(), user.getPassword())) {
                String email = user.getEmail() != null ? user.getEmail() : user.getPhone();
                String token = jwtUtil.GenerateToken(email);
                if (token == null) {
                    return ResponseEntity.status(500).body(new AuthResponse("Token generation failed", null, user.getId().toString(), "token generation failed"));
                }

                User authenticatedUser = userService.GetUserByEmailOrPhone(user.getEmail(), user.getPhone());
                AuthResponse authResponse = new AuthResponse("User authenticated successfully!", token, authenticatedUser.getId().toString(), "");
                return ResponseEntity.ok(authResponse);
            } else {
                return ResponseEntity.status(401).body(new AuthResponse("Invalid credentials", null, null, "Invalid credentials"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse("Something went wrong!", null, null, e.getMessage()));
        }
    }

    @GetMapping("/check-auth")
    public ResponseEntity<AuthResponse> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            // Extract token from "Bearer <token>"
            String token = authHeader.startsWith("Bearer ")
                    ? authHeader.substring(7)
                    : authHeader;

            if (expiredTokenService.isTokenExpired(token)) {
                return ResponseEntity.status(401).body(new AuthResponse(null, null, null, "Token has expired"));
            }

            if (jwtUtil.ValidateToken(token)) {
                return ResponseEntity.ok(new AuthResponse("Token is valid", token, null, ""));
            } else {
                return ResponseEntity.status(401).body(new AuthResponse("invalid token!", null, null, "Invalid token"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new AuthResponse("Something went wrong!", null, null, "Error validating token: " + e.getMessage())
            );
        }
    }

    @GetMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            // Extract token from "Bearer <token>"
            String token = authHeader.startsWith("Bearer ")
                    ? authHeader.substring(7)
                    : authHeader;

            if (jwtUtil.ValidateToken(token)) {
                // Invalidate the token logic can be added here if needed
                try {
                    String email = jwtUtil.GetStringFromToken(token);
                    if (email == null) {
                        return ResponseEntity.badRequest().body(new AuthResponse(
                                "Invalid token: email not found", null, null, "invalid token"
                        ));
                    }
                    User user = userService.GetUserByEmailOrPhone(email, email);
                    if (user == null) {
                        return ResponseEntity.badRequest().body(new AuthResponse(
                                "User not found", null, null, "invalid user"
                        ));
                    }

                    ExpiredToken expiredToken = new ExpiredToken();
                    expiredToken.setToken(token);
                    expiredToken.setUser(user);
                    try {
                        expiredTokenService.saveExpiredToken(expiredToken);
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body(new AuthResponse(
                                "Error saving expired token: " + e.getMessage(), null, null, "internal server error"
                        ));
                    }
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(new AuthResponse(
                            "Error invalidating token: " + e.getMessage(), null, null, "internal server error"
                    ));
                }

                return ResponseEntity.ok("User logged out successfully!");
            } else {
                return ResponseEntity.status(401).body(
                        new AuthResponse(null, null, null, "Invalid token")
                );
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new AuthResponse("Error logging out: " + e.getMessage(), null, null, "internal server error")
            );
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        try {
            User user = userService.GetUserById(id);
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
            User existingUser = userService.GetUserById(user.getId().toString());
            if (existingUser == null) {
                return ResponseEntity.status(404).body(new UserRes(null, "User not found"));
            }
            // Update fields
            if (user.getEmail() != null) existingUser.setEmail(user.getEmail());
            if (user.getPhone() != null) existingUser.setPhone(user.getPhone());
            if (user.getCurrency() != null) existingUser.setCurrency(user.getCurrency());
            if (user.getName() != null) existingUser.setName(user.getName());
            if (user.getCountry_code() != null) existingUser.setCountry_code(user.getCountry_code());
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
            User existingUser = userService.GetUserById(user.getId().toString());
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

            userService.UpdateUser(existingUser);
            return ResponseEntity.ok(new UserRes(existingUser, null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
        }
    }

    @PatchMapping("/update-password")
    public ResponseEntity<?> updatePassword(@RequestBody User user) {
        try {
            User existingUser = userService.GetUserById(user.getId().toString());
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
            userService.UpdateUser(user);
            return ResponseEntity.ok(new UserRes(null, null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
        }
    }

    @PatchMapping("/{id}/update-profile-picture")
    public ResponseEntity<?> updateProfilePicture(@PathVariable String id, @RequestParam("filepath") String filePath) {
        try {
            User user = userService.GetUserById(id);
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
            System.out.println("Verifying OAuth token: " + token + " " + environment.getProperty("GOOGLE_CLIENT_ID"));
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
                        return ResponseEntity.badRequest().body(new AuthResponse("Email or Phone is required!", null, null, "email or phone is required"));
                    }


                    if (userService.isUserPresent(user.getEmail(), user.getPhone())) {
                        User existingUser = userService.GetUserByEmailOrPhone(user.getEmail(), user.getPhone());
                        user.setOauth2User(true);
                        String jwtToken = jwtUtil.GenerateToken(existingUser.getEmail());
                        return ResponseEntity.ok(new AuthResponse("User authenticated successfully!", jwtToken, existingUser.getId().toString(), ""));
                    } else {
                        // Register new user
                        user.setOauth2User(true);
                        userService.save(user);
                        String jwtToken = jwtUtil.GenerateToken(user.getEmail());
                        return ResponseEntity.ok(new AuthResponse("profile incomplete", jwtToken, user.getId().toString(), ""));
                    }
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(new AuthResponse("Something went wrong!", null, null, e.getMessage()));
                }
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("Invalid ID token", null, null, "Invalid ID token"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("Error verifying token:", null, null, "Error verifying token"));
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


}
