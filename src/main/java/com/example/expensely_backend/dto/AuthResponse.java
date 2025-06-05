package com.example.expensely_backend.dto;

import lombok.Getter;

public class AuthResponse {

    @Getter
    private String message;
    @Getter
    private String token;
    @Getter
    private String id;
    @Getter
    private String error;

    public AuthResponse(String message, String token, String id, String error) {
        this.message = message;
        this.token = token;
        this.id = id;
        this.error = error;
    }

}
