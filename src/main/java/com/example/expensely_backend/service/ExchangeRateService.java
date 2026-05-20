package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.ExchangeRateApiResponse;
import com.example.expensely_backend.model.ExchangeRate;
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
	private static final String BASE_CURRENCY = "USD";
	private static final int DISPLAY_SCALE = 2;
	private static final int RATE_SCALE = 8;

	private final ExchangeRateRepository
			exchangeRateRepository;

	private final ObjectMapper objectMapper;

	public ExchangeRateService(
			ExchangeRateRepository
					exchangeRateRepository,
			ObjectMapper objectMapper
	) {
		this.exchangeRateRepository =
				exchangeRateRepository;
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
								LocalDateTime.now()
						);

				exchangeRates.add(
						exchangeRate
				);
			}

			exchangeRateRepository
					.saveAll(
							exchangeRates
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
		if (targetCurrency == null || targetCurrency.isBlank() || BASE_CURRENCY.equalsIgnoreCase(targetCurrency)) {
			return BigDecimal.ONE;
		}
		Optional<ExchangeRate> rate = exchangeRateRepository
				.findByBaseCurrencyAndTargetCurrency(BASE_CURRENCY, targetCurrency.toUpperCase());
		return rate.map(ExchangeRate::getRate)
				.orElseThrow(() -> new IllegalArgumentException("Exchange rate not found for " + BASE_CURRENCY + " -> " + targetCurrency));
	}

	public BigDecimal convertToUsd(BigDecimal amount, String currency) {
		if (amount == null) {
			return null;
		}
		if (currency == null || currency.isBlank() || BASE_CURRENCY.equalsIgnoreCase(currency)) {
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
		if (targetCurrency == null || targetCurrency.isBlank() || BASE_CURRENCY.equalsIgnoreCase(targetCurrency)) {
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
}
