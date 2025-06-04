package com.example.expensely_backend.dto;

import org.springframework.context.annotation.Bean;

public class AuthResponse {

    private String message;
    private String token;

    public AuthResponse(String message, String token) {
        this.message = message;
        this.token = token;
    }
    public String getMessage() {
        return message;
    }
    public String getToken() {
        return token;
    }

}
