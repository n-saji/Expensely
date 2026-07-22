package com.example.expensely_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuthLinkRequest {
    private String provider;
    private String providerUserId;
    private String providerEmail;
}
