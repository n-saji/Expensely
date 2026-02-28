package com.example.expensely_backend.model;

import com.example.expensely_backend.globals.globals;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recurring_expenses")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RecurringExpense {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne
	private User user;

	@ManyToOne
	private Category category;

	private BigDecimal amount;

	private String description;

	private globals.Recurrence recurrence; // "daily", "weekly", "monthly"

	private LocalDate nextOccurrence;

	private boolean active;

	private LocalDate date;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}

