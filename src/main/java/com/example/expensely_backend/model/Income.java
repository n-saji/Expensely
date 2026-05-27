package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
		name = "incomes",
		indexes = {
				@Index(name = "idx_income_date_user_id", columnList = "user_id,income_date")
		}
)
public class Income {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Getter
	@Setter
	private UUID id;

	@ManyToOne
	@Getter
	@Setter
	private User user;

	@ManyToOne
	@Getter
	@Setter
	private Category category;

	@Column(nullable = false)
	@Getter
	@Setter
	private BigDecimal amount;

	@Getter
	@Setter
	private String description;

	@Column(name = "income_date")
	@Getter
	@Setter
	private LocalDateTime incomeDate;

	@Column(nullable = false, length = 3, columnDefinition = "varchar(3) default 'USD'")
	@Getter
	@Setter
	private String currency;

	@Column(name = "base_currency_amount", precision = 19, scale = 4)
	@Getter
	@Setter
	private BigDecimal baseCurrencyAmount;

	@Column(name = "base_currency", length = 3)
	@Getter
	@Setter
	private String baseCurrency; // usually USD

	@Column(name = "exchange_rate", precision = 19, scale = 8)
	@Getter
	@Setter
	private BigDecimal exchangeRate; // snapshot rate
}
