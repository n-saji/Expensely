package com.example.expensely_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuthLoginRequest {
    private String provider;
    private String providerUserId;
    private String email;
    private String name;
    private String image;
    private String token;
}
