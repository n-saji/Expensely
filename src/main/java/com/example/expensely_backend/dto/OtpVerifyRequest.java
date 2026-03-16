package com.example.expensely_backend.dto;

import lombok.Data;

@Data
public class OtpVerifyRequest {
	private String userId;
	private String otp;
}

