package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.AuthResponse;
import com.example.expensely_backend.model.ExpiredToken;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.ExpiredTokenService;
import com.example.expensely_backend.service.UserService;
import com.example.expensely_backend.utils.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final ExpiredTokenService expiredTokenService;

    public UserController(UserService userService, JwtUtil jwtUtil, ExpiredTokenService expiredTokenService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.expiredTokenService = expiredTokenService;
    }


    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try{
            userService.save(user);
            return ResponseEntity.ok(new AuthResponse("User registered successfully!", null, user.getId().toString(), ""));
        }catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse("Something went wrong!", null, user.getId().toString(), e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?>login(@RequestBody User user) {
        if (user.getEmail() == null && user.getPhone() == null) {
            {
                return ResponseEntity.badRequest().body(new AuthResponse("Email or Phone is required!", null, user.getId().toString(), "email or phone is required"));
            }

        }

        if (user.getPassword() == null) {
            return ResponseEntity.badRequest().body(new AuthResponse("Password is required!", null, user.getId().toString(), "password is required"));
        }

        try {
            if (userService.authenticate(user.getEmail(), user.getPhone(), user.getPassword())){
                String email = user.getEmail() != null ? user.getEmail() : user.getPhone();
                String token = jwtUtil.GenerateToken(email);
                if (token == null) {
                    return ResponseEntity.status(500).body(new AuthResponse("Token generation failed", null, user.getId().toString(), "token generation failed"));
                }

                User authenticatedUser = userService.GetUserByEmailOrPhone(user.getEmail(), user.getPhone());
                AuthResponse authResponse = new AuthResponse("User authenticated successfully!", token, authenticatedUser.getId().toString(),"");
                return ResponseEntity.ok(authResponse);
            }
            else {
                return ResponseEntity.status(401).body(new AuthResponse("Invalid credentials",null,null,"Invalid credentials"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse("Something went wrong!",null,null,e.getMessage()));
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
                    try {expiredTokenService.saveExpiredToken(expiredToken);
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body(new AuthResponse(
                                "Error saving expired token: " + e.getMessage(), null, null, "internal server error"
                        ));
                    }
                }catch (Exception e) {
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


}
