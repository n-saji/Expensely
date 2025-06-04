package com.example.expensely_backend.dto;

import lombok.Getter;

public class AuthResponse {

    @Getter
    private String message;
    @Getter
    private String token;
    @Getter
    private String id;

    public AuthResponse(String message, String token, String id) {
        this.message = message;
        this.token = token;
        this.id = id;

    }

}
