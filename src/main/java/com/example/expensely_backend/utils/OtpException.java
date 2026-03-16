package com.example.expensely_backend.utils;

import org.springframework.http.HttpStatus;

public class OtpException extends RuntimeException {
	private final HttpStatus status;

	public OtpException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}

