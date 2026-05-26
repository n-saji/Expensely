package com.example.expensely_backend.dto;

import lombok.Getter;

import java.math.BigDecimal;

public class ExchangeRateConversionResponse {

	@Getter
	private final String baseCurrency;
	@Getter
	private final String targetCurrency;
	@Getter
	private final BigDecimal rate;
	@Getter
	private final BigDecimal amount;
	@Getter
	private final BigDecimal convertedAmount;

	public ExchangeRateConversionResponse(
			String baseCurrency,
			String targetCurrency,
			BigDecimal rate,
			BigDecimal amount,
			BigDecimal convertedAmount
	) {
		this.baseCurrency = baseCurrency;
		this.targetCurrency = targetCurrency;
		this.rate = rate;
		this.amount = amount;
		this.convertedAmount = convertedAmount;
	}
}

