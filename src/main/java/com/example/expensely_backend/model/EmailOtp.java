package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_otps", uniqueConstraints = {
		@UniqueConstraint(columnNames = "user_id")
})
public class EmailOtp {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Getter
	@Setter
	private UUID id;

	@OneToOne(optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	@Getter
	@Setter
	private User user;

	@Column(nullable = false)
	@Getter
	@Setter
	private String otpHash;

	@Column(nullable = false)
	@Getter
	@Setter
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	@Getter
	@Setter
	private LocalDateTime lastSentAt;

	@Column(nullable = false)
	@Getter
	@Setter
	private Integer failedAttempts = 0;

	@Column
	@Getter
	@Setter
	private LocalDateTime lockedUntil;
}
