package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table(name = "exchange_rates", uniqueConstraints = {@UniqueConstraint(columnNames = {"base_currency", "target_currency"})})
@Entity
@Getter
@Setter
public class ExchangeRate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "base_currency", nullable = false, length = 3,
			columnDefinition = "varchar(3) default 'USD'")
	private String baseCurrency;

	@Column(name = "target_currency", nullable = false, length = 3)
	private String targetCurrency;

	@Column(name = "rate", nullable = false, precision = 19, scale = 8)
	private BigDecimal rate;

	@Column(name = "fetched_at", nullable = false)
	private LocalDateTime updatedAt;


}
