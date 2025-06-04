package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.AuthResponse;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.UserService;
import com.example.expensely_backend.utils.JwtUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }


    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        try{
            userService.save(user);
            return ResponseEntity.ok("User registered successfully!");
        }catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?>login(@RequestBody User user) {
        if (user.getEmail() == null && user.getPhone() == null) {
            {
                return ResponseEntity.badRequest().body("Email or phone must not be null");
            }

        }

        if (user.getPassword() == null) {
            return ResponseEntity.badRequest().body("Password must not be null");
        }

        try {
            if (userService.authenticate(user.getEmail(), user.getPhone(), user.getPassword())){
                String token = jwtUtil.GenerateToken(user.getEmail());
                if (token == null) {
                    return ResponseEntity.status(500).body("Error generating token");
                }

                return ResponseEntity.ok(new AuthResponse("User authenticated successfully!", token));
            }
            else {
                return ResponseEntity.status(401).body("Invalid credentials");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

}
