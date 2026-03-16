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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailOtpService {
	private static final Duration OTP_TTL = Duration.ofMinutes(10);
	private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(2);
	private static final int FREE_ATTEMPTS = 3;
	private static final int[] LOCKOUT_MINUTES = new int[]{1, 5, 10};
	public static final String PURPOSE_EMAIL_VERIFY = "EMAIL_VERIFY";
	public static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";

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
		EmailOtp emailOtp = getOrCreateOtp(user, PURPOSE_EMAIL_VERIFY);

		String otp = generateOtp();
		emailOtp.setOtpHash(passwordEncoder.encode(otp));
		emailOtp.setPurpose(PURPOSE_EMAIL_VERIFY);
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

		EmailOtp emailOtp = getOrCreateOtp(user, PURPOSE_EMAIL_VERIFY);

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
		emailOtp.setPurpose(PURPOSE_EMAIL_VERIFY);
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

		EmailOtp emailOtp = emailOtpRepository.findByUserIdAndPurpose(user.getId(), PURPOSE_EMAIL_VERIFY)
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

	public Optional<ResetTokenDetails> createPasswordResetToken(String email) {
		if (email == null || email.isBlank()) {
			throw new OtpException(HttpStatus.BAD_REQUEST, "email is required");
		}
		Optional<User> userOpt = userRepository.findByEmail(email);
		if (userOpt.isEmpty()) {
			return Optional.empty();
		}
		User user = userOpt.get();
		EmailOtp emailOtp = getOrCreateOtp(user, PURPOSE_PASSWORD_RESET);

		if (emailOtp.getLastSentAt() != null) {
			LocalDateTime cooldownUntil = emailOtp.getLastSentAt().plus(RESEND_COOLDOWN);
			if (LocalDateTime.now().isBefore(cooldownUntil)) {
				return Optional.empty();
			}
		}

		String tokenHash = generateResetTokenHash();
		emailOtp.setOtpHash(tokenHash);
		emailOtp.setPurpose(PURPOSE_PASSWORD_RESET);
		emailOtp.setExpiresAt(LocalDateTime.now().plus(OTP_TTL));
		emailOtp.setLastSentAt(LocalDateTime.now());
		emailOtp.setFailedAttempts(0);
		emailOtp.setLockedUntil(null);
		emailOtpRepository.save(emailOtp);
		return Optional.of(new ResetTokenDetails(user.getId().toString(), tokenHash));
	}

	public User validatePasswordResetToken(String userId, String tokenHash) {
		if (tokenHash == null || tokenHash.isBlank()) {
			throw new OtpException(HttpStatus.BAD_REQUEST, "reset token is required");
		}
		User user = getUserById(userId);
		EmailOtp emailOtp = emailOtpRepository.findByUserIdAndPurpose(user.getId(), PURPOSE_PASSWORD_RESET)
				.orElseThrow(() -> new OtpException(HttpStatus.BAD_REQUEST, "reset token not found"));

		if (emailOtp.getLockedUntil() != null && LocalDateTime.now().isBefore(emailOtp.getLockedUntil())) {
			long seconds = Duration.between(LocalDateTime.now(), emailOtp.getLockedUntil()).getSeconds();
			throw new OtpException(HttpStatus.TOO_MANY_REQUESTS,
					"too many attempts, try again in " + seconds + " seconds");
		}

		if (emailOtp.getExpiresAt() != null && LocalDateTime.now().isAfter(emailOtp.getExpiresAt())) {
			throw new OtpException(HttpStatus.BAD_REQUEST, "reset token expired");
		}

		if (constantTimeEquals(tokenHash, emailOtp.getOtpHash())) {
			emailOtpRepository.delete(emailOtp);
			return user;
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
		throw new OtpException(HttpStatus.BAD_REQUEST, "invalid reset token");
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

	private EmailOtp getOrCreateOtp(User user, String purpose) {
		EmailOtp emailOtp = emailOtpRepository.findByUserIdAndPurpose(user.getId(), purpose)
				.orElseGet(() -> {
					EmailOtp fresh = new EmailOtp();
					fresh.setUser(user);
					return fresh;
				});
		if (emailOtp.getPurpose() == null || emailOtp.getPurpose().isBlank()) {
			emailOtp.setPurpose(purpose);
		}
		return emailOtp;
	}

	private String generateResetTokenHash() {
		byte[] raw = new byte[32];
		secureRandom.nextBytes(raw);
		return sha256Hex(raw);
	}

	private String sha256Hex(byte[] bytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(bytes);
			StringBuilder sb = new StringBuilder(hashed.length * 2);
			for (byte b : hashed) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException("Unable to hash reset token", e);
		}
	}

	private boolean constantTimeEquals(String left, String right) {
		if (left == null || right == null) {
			return false;
		}
		return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
	}

	public static class ResetTokenDetails {
		private final String userId;
		private final String otpHash;

		public ResetTokenDetails(String userId, String otpHash) {
			this.userId = userId;
			this.otpHash = otpHash;
		}

		public String getUserId() {
			return userId;
		}

		public String getOtpHash() {
			return otpHash;
		}
	}
}
