package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.ExchangeRateApiResponse;
import com.example.expensely_backend.dto.ExchangeRateHistoryPoint;
import com.example.expensely_backend.dto.ExchangeRateHistoryResponse;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.ExchangeRate;
import com.example.expensely_backend.model.ExchangeRateHistory;
import com.example.expensely_backend.repository.ExchangeRateHistoryRepository;
import com.example.expensely_backend.repository.ExchangeRateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ExchangeRateService {

	private static final int DISPLAY_SCALE = 2;
	private static final int RATE_SCALE = 8;

	private final ExchangeRateRepository
			exchangeRateRepository;

	private final ExchangeRateHistoryRepository
			exchangeRateHistoryRepository;

	private final ObjectMapper objectMapper;

	public ExchangeRateService(
			ExchangeRateRepository
					exchangeRateRepository,
			ExchangeRateHistoryRepository
					exchangeRateHistoryRepository,
			ObjectMapper objectMapper
	) {
		this.exchangeRateRepository =
				exchangeRateRepository;
		this.exchangeRateHistoryRepository =
				exchangeRateHistoryRepository;
		this.objectMapper =
				objectMapper;
	}

	@Transactional
	@Scheduled(cron = "0 0 */5 * * *")
	public void updateExchangeRates() {

		try {

			HttpClient client =
					HttpClient.newHttpClient();

			HttpRequest request =
					HttpRequest.newBuilder()
							.uri(
									URI.create(
											"https://exchange-rateapi.com/api/v1/rates?source=USD"
									)
							)
							.GET()
							.header(
									"Accept",
									"application/json"
							)
							.header(
									"Authorization",
									"Bearer "
											+ System.getenv(
											"EXCHANGE_RATE_API_KEY"
									)
							)
							.build();

			HttpResponse<String> response =
					client.send(
							request,
							HttpResponse
									.BodyHandlers
									.ofString()
					);

			if (response.statusCode() != 200) {

				System.err.println(
						"Failed to fetch exchange rates: "
								+ response.statusCode()
				);

				return;
			}

			List<ExchangeRateApiResponse>
					apiRates =
					objectMapper.readValue(
							response.body(),
							new TypeReference<>() {
							}
					);

			List<ExchangeRate>
					exchangeRates =
					new ArrayList<>();

			List<ExchangeRateHistory>
					historyRows =
					new ArrayList<>();

			LocalDateTime
					fetchedAt =
					LocalDateTime.now();

			for (
					ExchangeRateApiResponse
							apiRate
					: apiRates
			) {

				Optional<ExchangeRate>
						existingRate =
						exchangeRateRepository
								.findByBaseCurrencyAndTargetCurrency(
										apiRate.getSource(),
										apiRate.getTarget()
								);

				ExchangeRate
						exchangeRate =
						existingRate.orElse(
								new ExchangeRate()
						);

				exchangeRate
						.setBaseCurrency(
								apiRate.getSource()
						);

				exchangeRate
						.setTargetCurrency(
								apiRate.getTarget()
						);

				exchangeRate
						.setRate(
								BigDecimal.valueOf(apiRate.getRate())
						);

				exchangeRate
						.setUpdatedAt(
								fetchedAt
						);

				exchangeRates.add(
						exchangeRate
				);

				ExchangeRateHistory
						historyRow =
						new ExchangeRateHistory();

				historyRow
						.setBaseCurrency(
								apiRate.getSource()
						);

				historyRow
						.setTargetCurrency(
								apiRate.getTarget()
						);

				historyRow
						.setRate(
								BigDecimal.valueOf(apiRate.getRate())
						);

				historyRow
						.setRecordedAt(
								fetchedAt
						);

				historyRows.add(
						historyRow
				);
			}

			exchangeRateRepository
					.saveAll(
							exchangeRates
					);

			exchangeRateHistoryRepository
					.saveAll(
							historyRows
					);

			System.out.println(
					"Exchange rates updated successfully."
			);

		} catch (Exception e) {

			System.err.println(
					"Error updating exchange rates"
			);

			e.printStackTrace();
		}
	}

	public BigDecimal getUsdToCurrencyRate(String targetCurrency) {
		if (targetCurrency == null || targetCurrency.isBlank() || globals.BASE_CURRENCY.equalsIgnoreCase(targetCurrency)) {
			return BigDecimal.ONE;
		}
		Optional<ExchangeRate> rate = exchangeRateRepository
				.findByBaseCurrencyAndTargetCurrency(globals.BASE_CURRENCY, targetCurrency.toUpperCase());
		return rate.map(ExchangeRate::getRate)
				.orElseThrow(() -> new IllegalArgumentException("Exchange rate not found for " + globals.BASE_CURRENCY + " -> " + targetCurrency));
	}

	public BigDecimal convertToUsd(BigDecimal amount, String currency) {
		if (amount == null) {
			return null;
		}
		if (currency == null || currency.isBlank() || globals.BASE_CURRENCY.equalsIgnoreCase(currency)) {
			return amount.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
		}
		BigDecimal rate = getUsdToCurrencyRate(currency);
		return amount
				.divide(rate, RATE_SCALE, RoundingMode.HALF_UP)
				.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
	}

	public BigDecimal convertFromUsd(BigDecimal baseAmount, String targetCurrency) {
		if (baseAmount == null) {
			return null;
		}
		if (targetCurrency == null || targetCurrency.isBlank() || globals.BASE_CURRENCY.equalsIgnoreCase(targetCurrency)) {
			return baseAmount.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
		}
		BigDecimal rate = getUsdToCurrencyRate(targetCurrency);
		return baseAmount
				.multiply(rate)
				.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
	}

	public BigDecimal normalizeDisplayAmount(BigDecimal amount) {
		if (amount == null) {
			return null;
		}
		return amount.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
	}

	public List<ExchangeRate> getUsdRates(String targetCurrencyQuery) {
		String query = targetCurrencyQuery == null ? null : targetCurrencyQuery.trim();
		if (query == null || query.isBlank()) {
			return exchangeRateRepository
					.findByBaseCurrencyIgnoreCaseOrderByTargetCurrencyAsc(globals.BASE_CURRENCY);
		}
		return exchangeRateRepository
				.findByBaseCurrencyIgnoreCaseAndTargetCurrencyContainingIgnoreCaseOrderByTargetCurrencyAsc(
							globals.BASE_CURRENCY,
							query
				);
	}

	public BigDecimal getCrossRate(String baseCurrency, String targetCurrency) {
		String base = normalizeCurrencyOrDefault(baseCurrency);
		String target = normalizeCurrencyOrThrow(targetCurrency);
		if (base.equalsIgnoreCase(target)) {
			return BigDecimal.ONE.setScale(RATE_SCALE, RoundingMode.HALF_UP);
		}
		if (globals.BASE_CURRENCY.equalsIgnoreCase(base)) {
			return getUsdToCurrencyRate(target).setScale(RATE_SCALE, RoundingMode.HALF_UP);
		}
		if (globals.BASE_CURRENCY.equalsIgnoreCase(target)) {
			return BigDecimal.ONE.divide(
					getUsdToCurrencyRate(base),
					RATE_SCALE,
					RoundingMode.HALF_UP
			);
		}
		BigDecimal targetRate = getUsdToCurrencyRate(target);
		BigDecimal baseRate = getUsdToCurrencyRate(base);
		return targetRate.divide(baseRate, RATE_SCALE, RoundingMode.HALF_UP);
	}

	public BigDecimal convertAmount(BigDecimal amount, String baseCurrency, String targetCurrency) {
		BigDecimal resolvedAmount = amount == null ? BigDecimal.ONE : amount;
		BigDecimal rate = getCrossRate(baseCurrency, targetCurrency);
		return resolvedAmount.multiply(rate)
				.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
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

	public List<String> getAvailableCurrencies() {
		List<String> currencies = new ArrayList<>(exchangeRateRepository.findDistinctCurrencies());
		String base = globals.BASE_CURRENCY.toUpperCase();
		if (currencies.stream().noneMatch(currency -> currency.equalsIgnoreCase(base))) {
			currencies.add(base);
		}
		currencies.sort(String.CASE_INSENSITIVE_ORDER);
		return currencies;
	}

	public ExchangeRateHistoryResponse getRateHistoryResponse(String baseCurrency, String targetCurrency, String range) {
		String base = normalizeCurrencyOrDefault(baseCurrency);
		String target = normalizeCurrencyOrThrow(targetCurrency);
		String resolvedRange = range == null ? "all" : range.trim().toLowerCase();

		List<ExchangeRateHistory> history;
		Optional<LocalDateTime> since = resolveSince(resolvedRange);
		if (since.isPresent()) {
			history = exchangeRateHistoryRepository
					.findByBaseCurrencyIgnoreCaseAndTargetCurrencyIgnoreCaseAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
							base, target, since.get());
		} else {
			resolvedRange = "all";
			history = exchangeRateHistoryRepository
					.findByBaseCurrencyIgnoreCaseAndTargetCurrencyIgnoreCaseOrderByRecordedAtAsc(base, target);
		}

		List<ExchangeRateHistoryPoint> points = history.stream()
				.map(ExchangeRateHistoryPoint::new)
				.toList();

		BigDecimal currentRate;
		BigDecimal high = null;
		BigDecimal low = null;
		BigDecimal periodChangeAbsolute = null;
		BigDecimal periodChangePercent = null;

		if (!history.isEmpty()) {
			BigDecimal firstRate = history.get(0).getRate();
			BigDecimal lastRate = history.get(history.size() - 1).getRate();
			currentRate = lastRate;

			high = history.stream().map(ExchangeRateHistory::getRate).max(BigDecimal::compareTo).orElse(null);
			low = history.stream().map(ExchangeRateHistory::getRate).min(BigDecimal::compareTo).orElse(null);

			if (history.size() > 1) {
				periodChangeAbsolute = lastRate.subtract(firstRate).setScale(RATE_SCALE, RoundingMode.HALF_UP);
				if (firstRate.compareTo(BigDecimal.ZERO) != 0) {
					periodChangePercent = lastRate.subtract(firstRate)
							.divide(firstRate, RATE_SCALE, RoundingMode.HALF_UP)
							.multiply(BigDecimal.valueOf(100))
							.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
				}
			}
		} else {
			currentRate = getCrossRate(base, target);
		}

		return new ExchangeRateHistoryResponse(
				base,
				target,
				resolvedRange,
				currentRate,
				periodChangeAbsolute,
				periodChangePercent,
				high,
				low,
				points
		);
	}

	private Optional<LocalDateTime> resolveSince(String range) {
		LocalDateTime now = LocalDateTime.now();
		return switch (range) {
			case "7d" -> Optional.of(now.minusDays(7));
			case "30d" -> Optional.of(now.minusDays(30));
			case "90d" -> Optional.of(now.minusDays(90));
			case "1y" -> Optional.of(now.minusYears(1));
			default -> Optional.empty();
		};
	}
}
