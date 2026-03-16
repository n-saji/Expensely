package com.example.expensely_backend.dto;

import lombok.Data;

@Data
public class PasswordResetConfirmRequest {
	private String userId;
	private String otp;
	private String newPassword;
}

