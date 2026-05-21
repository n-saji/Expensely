package com.example.expensely_backend.model;

import com.example.expensely_backend.globals.globals;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
		name = "budgets"
//        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category_id", "startDate", "endDate", "isActive"})
)
public class Budget {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Getter
	@Setter
	@Column(columnDefinition = "UUID", updatable = false, nullable = false)
	private UUID id;

	@ManyToOne
	@Setter
	@Getter
	private User user;

	@ManyToOne
	@Setter
	@Getter
	private Category category;

	@Getter
	@Setter
	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal amountLimit;

	@Getter
	@Setter
	@Column(precision = 15, scale = 2)
	private BigDecimal amountSpent; // store base currency amount

	@Column(nullable = false, length = 3, columnDefinition = "varchar(3) default 'USD'")
	@Getter
	@Setter
	private String currency;

	@Column(name = "base_currency_amount", precision = 19, scale = 4)
	@Getter
	@Setter
	private BigDecimal baseCurrencyAmount; // the base currency is USD
	// accross the system

	@Column(name = "exchange_rate", precision = 19, scale = 8)
	@Getter
	@Setter
	private BigDecimal exchangeRate; // snapshot rate

	@Getter
	@Setter
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private globals.Period period;

	@Getter
	@Setter
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Getter
	@Setter
	@Column(name = "end_date")
	private LocalDate endDate;

	@Getter
	@Setter
	@Column(name = "is_active", columnDefinition = "boolean default true")
	private boolean isActive = true;

	@Getter
	@Setter
	@Column(name = "created_at")
	private LocalDateTime createdAt = LocalDateTime.now();

	@Getter
	@Setter
	@Column(name = "updated_at")
	private LocalDateTime updatedAt = LocalDateTime.now();

	@Getter
	@Setter
	@Column(name = "threshold_50_crossed", nullable = false, columnDefinition = "boolean default false")
	private boolean isThreshold50Crossed = false;

	@Getter
	@Setter
	@Column(name = "threshold_75_crossed", nullable = false, columnDefinition = "boolean default false")
	private boolean isThreshold75Crossed = false;

	@Getter
	@Setter
	@Column(name = "threshold_100_crossed", nullable = false, columnDefinition = "boolean default false")
	private boolean isThreshold100Crossed = false;


}
