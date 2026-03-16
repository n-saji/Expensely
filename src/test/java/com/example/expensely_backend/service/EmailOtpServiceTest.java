package com.example.expensely_backend.service;

import com.example.expensely_backend.model.EmailOtp;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.EmailOtpRepository;
import com.example.expensely_backend.repository.UserRepository;
import com.example.expensely_backend.utils.OtpException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailOtpServiceTest {

	@Test
	void verifyOtpLocksAfterThreeFailures() {
		EmailOtpRepository emailOtpRepository = mock(EmailOtpRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

		User user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail("user@example.com");
		user.setEmailVerified(false);

		EmailOtp otp = new EmailOtp();
		otp.setUser(user);
		otp.setOtpHash(passwordEncoder.encode("123456"));
		otp.setPurpose(EmailOtpService.PURPOSE_EMAIL_VERIFY);
		otp.setExpiresAt(LocalDateTime.now().plusMinutes(10));
		otp.setLastSentAt(LocalDateTime.now());
		otp.setFailedAttempts(0);

		AtomicReference<EmailOtp> stored = new AtomicReference<>(otp);

		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(emailOtpRepository.findByUserIdAndPurpose(user.getId(), EmailOtpService.PURPOSE_EMAIL_VERIFY))
				.thenAnswer(inv -> Optional.ofNullable(stored.get()));
		when(emailOtpRepository.save(any(EmailOtp.class))).thenAnswer(inv -> {
			stored.set(inv.getArgument(0));
			return stored.get();
		});

		EmailOtpService service = new EmailOtpService(emailOtpRepository, userRepository, passwordEncoder);

		assertThrows(OtpException.class, () -> service.verifyOtp(user.getId().toString(), "000000"));
		assertThrows(OtpException.class, () -> service.verifyOtp(user.getId().toString(), "000000"));
		OtpException locked = assertThrows(OtpException.class,
				() -> service.verifyOtp(user.getId().toString(), "000000"));
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, locked.getStatus());
	}

	@Test
	void verifyOtpResetsFailedAttemptsOnSuccess() {
		EmailOtpRepository emailOtpRepository = mock(EmailOtpRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

		User user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail("user@example.com");
		user.setEmailVerified(false);

		EmailOtp otp = new EmailOtp();
		otp.setUser(user);
		otp.setOtpHash(passwordEncoder.encode("123456"));
		otp.setPurpose(EmailOtpService.PURPOSE_EMAIL_VERIFY);
		otp.setExpiresAt(LocalDateTime.now().plusMinutes(10));
		otp.setLastSentAt(LocalDateTime.now());
		otp.setFailedAttempts(2);

		AtomicReference<EmailOtp> stored = new AtomicReference<>(otp);

		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(emailOtpRepository.findByUserIdAndPurpose(user.getId(), EmailOtpService.PURPOSE_EMAIL_VERIFY))
				.thenAnswer(inv -> Optional.ofNullable(stored.get()));
		when(emailOtpRepository.save(any(EmailOtp.class))).thenAnswer(inv -> {
			stored.set(inv.getArgument(0));
			return stored.get();
		});
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		EmailOtpService service = new EmailOtpService(emailOtpRepository, userRepository, passwordEncoder);

		service.verifyOtp(user.getId().toString(), "123456");
		assertEquals(0, stored.get().getFailedAttempts());
		assertEquals(true, user.isEmailVerified());
	}

	@Test
	void validatePasswordResetTokenDeletesOnSuccess() {
		EmailOtpRepository emailOtpRepository = mock(EmailOtpRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

		User user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail("user@example.com");

		EmailOtp otp = new EmailOtp();
		otp.setUser(user);
		otp.setOtpHash("reset-hash");
		otp.setPurpose(EmailOtpService.PURPOSE_PASSWORD_RESET);
		otp.setExpiresAt(LocalDateTime.now().plusMinutes(10));
		otp.setLastSentAt(LocalDateTime.now());
		otp.setFailedAttempts(0);

		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(emailOtpRepository.findByUserIdAndPurpose(user.getId(), EmailOtpService.PURPOSE_PASSWORD_RESET))
				.thenReturn(Optional.of(otp));

		EmailOtpService service = new EmailOtpService(emailOtpRepository, userRepository, passwordEncoder);

		service.validatePasswordResetToken(user.getId().toString(), "reset-hash");
		verify(emailOtpRepository).delete(otp);
	}
}

