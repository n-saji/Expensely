package com.example.expensely_backend.dto;

import lombok.Getter;

public class AuthResponse {

    @Getter
    private final String message;
    @Getter
    private final String token;
    @Getter
    private final String id;
    @Getter
    private final String error;
    @Getter
    private boolean profileIncomplete = false;

    public AuthResponse(String message, String token, String id, String error) {
        this.message = message;
        this.token = token;
        this.id = id;
        this.error = error;
        if (message != null && message.contains("profile incomplete")) {
            this.profileIncomplete = true;
        }
    }

}
