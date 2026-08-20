package com.example.expensely_backend.dto;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

public class ExchangeRateHistoryResponse {

	@Getter
	private final String baseCurrency;
	@Getter
	private final String targetCurrency;
	@Getter
	private final String range;
	@Getter
	private final BigDecimal currentRate;
	@Getter
	private final BigDecimal periodChangeAbsolute;
	@Getter
	private final BigDecimal periodChangePercent;
	@Getter
	private final BigDecimal high;
	@Getter
	private final BigDecimal low;
	@Getter
	private final List<ExchangeRateHistoryPoint> points;

	public ExchangeRateHistoryResponse(
			String baseCurrency,
			String targetCurrency,
			String range,
			BigDecimal currentRate,
			BigDecimal periodChangeAbsolute,
			BigDecimal periodChangePercent,
			BigDecimal high,
			BigDecimal low,
			List<ExchangeRateHistoryPoint> points
	) {
		this.baseCurrency = baseCurrency;
		this.targetCurrency = targetCurrency;
		this.range = range;
		this.currentRate = currentRate;
		this.periodChangeAbsolute = periodChangeAbsolute;
		this.periodChangePercent = periodChangePercent;
		this.high = high;
		this.low = low;
		this.points = points;
	}
}
