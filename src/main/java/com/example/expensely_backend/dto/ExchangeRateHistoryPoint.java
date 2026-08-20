package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.ExchangeRateHistory;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExchangeRateHistoryPoint {

	@Getter
	private final BigDecimal rate;
	@Getter
	private final LocalDateTime recordedAt;

	public ExchangeRateHistoryPoint(ExchangeRateHistory history) {
		this.rate = history.getRate();
		this.recordedAt = history.getRecordedAt();
	}
}
