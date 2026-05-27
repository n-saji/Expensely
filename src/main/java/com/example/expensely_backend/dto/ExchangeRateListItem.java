package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.ExchangeRate;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExchangeRateListItem {

	@Getter
	private final String baseCurrency;
	@Getter
	private final String targetCurrency;
	@Getter
	private final BigDecimal rate;
	@Getter
	private final LocalDateTime updatedAt;

	public ExchangeRateListItem(ExchangeRate rate) {
		this.baseCurrency = rate.getBaseCurrency();
		this.targetCurrency = rate.getTargetCurrency();
		this.rate = rate.getRate();
		this.updatedAt = rate.getUpdatedAt();
	}
}

