package com.example.expensely_backend.service;

import com.example.expensely_backend.model.EmailOtp;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.EmailOtpRepository;
import com.example.expensely_backend.repository.UserRepository;
import com.example.expensely_backend.utils.OtpException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailOtpService {
	private static final Duration OTP_TTL = Duration.ofMinutes(10);
	private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(2);
	private static final int FREE_ATTEMPTS = 3;
	private static final int[] LOCKOUT_MINUTES = new int[]{1, 5, 10};

	private final EmailOtpRepository emailOtpRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final SecureRandom secureRandom = new SecureRandom();

	public EmailOtpService(EmailOtpRepository emailOtpRepository,
	                       UserRepository userRepository,
	                       PasswordEncoder passwordEncoder) {
		this.emailOtpRepository = emailOtpRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public String createOrUpdateOtp(User user) {
		EmailOtp emailOtp = emailOtpRepository.findByUserId(user.getId())
				.orElseGet(() -> {
					EmailOtp fresh = new EmailOtp();
					fresh.setUser(user);
					return fresh;
				});

		String otp = generateOtp();
		emailOtp.setOtpHash(passwordEncoder.encode(otp));
		emailOtp.setExpiresAt(LocalDateTime.now().plus(OTP_TTL));
		emailOtp.setLastSentAt(LocalDateTime.now());
		emailOtpRepository.save(emailOtp);
		return otp;
	}

	public String resendOtp(String userId) {
		User user = getUserById(userId);
		if (user.isEmailVerified()) {
			throw new OtpException(HttpStatus.BAD_REQUEST, "email already verified");
		}

		EmailOtp emailOtp = emailOtpRepository.findByUserId(user.getId())
				.orElseGet(() -> {
					EmailOtp fresh = new EmailOtp();
					fresh.setUser(user);
					return fresh;
				});

		if (emailOtp.getLastSentAt() != null) {
			LocalDateTime cooldownUntil = emailOtp.getLastSentAt().plus(RESEND_COOLDOWN);
			if (LocalDateTime.now().isBefore(cooldownUntil)) {
				long seconds = Duration.between(LocalDateTime.now(), cooldownUntil).getSeconds();
				throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
						"otp resend cooldown active, try again in " + seconds + " seconds");
			}
		}

		String otp = generateOtp();
		emailOtp.setOtpHash(passwordEncoder.encode(otp));
		emailOtp.setExpiresAt(LocalDateTime.now().plus(OTP_TTL));
		emailOtp.setLastSentAt(LocalDateTime.now());
		emailOtpRepository.save(emailOtp);
		return otp;
	}

	public void verifyOtp(String userId, String otp) {
		if (otp == null || otp.isBlank()) {
			throw new OtpException(HttpStatus.BAD_REQUEST, "otp is required");
		}

		User user = getUserById(userId);
		if (user.isEmailVerified()) {
			throw new OtpException(HttpStatus.BAD_REQUEST, "email already verified");
		}

		EmailOtp emailOtp = emailOtpRepository.findByUserId(user.getId())
				.orElseThrow(() -> new OtpException(HttpStatus.BAD_REQUEST, "otp not found"));

		if (emailOtp.getLockedUntil() != null && LocalDateTime.now().isBefore(emailOtp.getLockedUntil())) {
			long seconds = Duration.between(LocalDateTime.now(), emailOtp.getLockedUntil()).getSeconds();
			throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
					"too many attempts, try again in " + seconds + " seconds");
		}

		if (emailOtp.getExpiresAt() != null && LocalDateTime.now().isAfter(emailOtp.getExpiresAt())) {
			throw new OtpException(HttpStatus.BAD_REQUEST, "otp expired");
		}

		if (passwordEncoder.matches(otp, emailOtp.getOtpHash())) {
			user.setEmailVerified(true);
			emailOtp.setFailedAttempts(0);
			emailOtp.setLockedUntil(null);
			userRepository.save(user);
			emailOtpRepository.save(emailOtp);
			return;
		}

		int attempts = Optional.ofNullable(emailOtp.getFailedAttempts()).orElse(0) + 1;
		emailOtp.setFailedAttempts(attempts);

		if (attempts >= FREE_ATTEMPTS) {
			int index = Math.min(attempts - FREE_ATTEMPTS, LOCKOUT_MINUTES.length - 1);
			int minutes = LOCKOUT_MINUTES[index];
			emailOtp.setLockedUntil(LocalDateTime.now().plusMinutes(minutes));
			emailOtpRepository.save(emailOtp);
			throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
					"too many attempts, try again in " + (minutes * 60L) + " seconds");
		}

		emailOtpRepository.save(emailOtp);
		throw new OtpException(HttpStatus.BAD_REQUEST, "invalid otp");
	}

	private User getUserById(String userId) {
		UUID uuid;
		try {
			uuid = UUID.fromString(userId);
		} catch (IllegalArgumentException e) {
			throw new OtpException(HttpStatus.BAD_REQUEST, "invalid user id");
		}
		return userRepository.findById(uuid)
				.orElseThrow(() -> new OtpException(HttpStatus.BAD_REQUEST, "user not found"));
	}

	private String generateOtp() {
		int value = secureRandom.nextInt(1_000_000);
		return String.format("%06d", value);
	}
}

