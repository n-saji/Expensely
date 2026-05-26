package com.example.expensely_backend.dto;

import lombok.Getter;

import java.util.List;

public class CurrencyListResponse {

	@Getter
	private final int count;
	@Getter
	private final List<String> currencies;

	public CurrencyListResponse(List<String> currencies) {
		this.currencies = currencies;
		this.count = currencies == null ? 0 : currencies.size();
	}
}

