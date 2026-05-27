package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.ExchangeRateConversionResponse;
import com.example.expensely_backend.dto.ExchangeRateListItem;
import com.example.expensely_backend.dto.CurrencyListResponse;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.ExchangeRate;
import com.example.expensely_backend.service.ExchangeRateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exchange-rates")
public class ExchangeRateController {

	private final ExchangeRateService exchangeRateService;

	public ExchangeRateController(ExchangeRateService exchangeRateService) {
		this.exchangeRateService = exchangeRateService;
	}

	@GetMapping("")
	public ResponseEntity<?> getUsdRates(
			@RequestParam(name = "targetCurrency", required = false) String targetCurrency
	) {
		try {
			List<ExchangeRate> rates = exchangeRateService.getUsdRates(targetCurrency);
			List<ExchangeRateListItem> response = rates.stream()
					.map(ExchangeRateListItem::new)
					.collect(Collectors.toList());
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	@GetMapping("/convert")
	public ResponseEntity<?> convert(
			@RequestParam(name = "baseCurrency", required = false) String baseCurrency,
			@RequestParam(name = "targetCurrency") String targetCurrency,
			@RequestParam(name = "amount", required = false) BigDecimal amount
	) {
		try {
			String resolvedBase = normalizeCurrencyOrDefault(baseCurrency);
			String resolvedTarget = normalizeCurrencyOrThrow(targetCurrency);
			BigDecimal resolvedAmount = amount == null ? BigDecimal.ONE : amount;
			BigDecimal rate = exchangeRateService.getCrossRate(resolvedBase, resolvedTarget);
			BigDecimal convertedAmount = exchangeRateService.convertAmount(resolvedAmount, resolvedBase, resolvedTarget);
			return ResponseEntity.ok(
					new ExchangeRateConversionResponse(
							resolvedBase,
							resolvedTarget,
							rate,
							resolvedAmount,
							convertedAmount
					)
			);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	@GetMapping("/currencies")
	public ResponseEntity<?> getAvailableCurrencies() {
		try {
			return ResponseEntity.ok(
					new CurrencyListResponse(exchangeRateService.getAvailableCurrencies())
			);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	private String normalizeCurrencyOrDefault(String currency) {
		if (currency == null || currency.isBlank()) {
			return globals.BASE_CURRENCY;
		}
		return currency.trim().toUpperCase();
	}

	private String normalizeCurrencyOrThrow(String currency) {
		if (currency == null || currency.isBlank()) {
			throw new IllegalArgumentException("Target currency is required");
		}
		return currency.trim().toUpperCase();
	}
}
