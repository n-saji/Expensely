package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
		name = "transactions",
		indexes = {
				@Index(name = "idx_transaction_date_user_id_type", columnList = "user_id,type,transaction_date")
		}
)
public class Transaction {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Getter
	@Setter
	@Column(columnDefinition = "UUID", updatable = false, nullable = false)
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

	@Getter
	@Setter
	private String description;

	@Column(name = "transaction_date")
	@Getter
	@Setter
	@com.fasterxml.jackson.annotation.JsonAlias({"expenseDate", "incomeDate", "transactionDate"})
	private LocalDateTime transactionDate;

	@Column(name = "receipt_url")
	@Getter
	@Setter
	private String receiptUrl; // nullable, only for EXPENSE

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	@Getter
	@Setter
	private TransactionType type;
}
