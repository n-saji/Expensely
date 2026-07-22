package com.example.expensely_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAccountDto {
    private String provider;
    private String providerUserId;
    private String providerEmail;
    private LocalDateTime createdAt;
}
