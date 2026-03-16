package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_otps", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"user_id", "purpose"})
})
public class EmailOtp {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Getter
	@Setter
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	@Getter
	@Setter
	private User user;

	@Column(nullable = false)
	@Getter
	@Setter
	private String otpHash;

	@Column(nullable = false, columnDefinition = "varchar(32) default 'EMAIL_VERIFY'")
	@Getter
	@Setter
	private String purpose = "EMAIL_VERIFY";

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
