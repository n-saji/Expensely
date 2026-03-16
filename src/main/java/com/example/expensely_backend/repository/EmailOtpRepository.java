package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {
	Optional<EmailOtp> findByUserId(UUID userId);
	Optional<EmailOtp> findByUserIdAndPurpose(UUID userId, String purpose);
}

