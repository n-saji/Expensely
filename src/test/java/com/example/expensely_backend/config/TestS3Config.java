package com.example.expensely_backend.config;

import com.example.expensely_backend.utils.S3Service;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestS3Config {
	@Bean
	@Primary
	public S3Service s3Service() {
		return Mockito.mock(S3Service.class);
	}
}

