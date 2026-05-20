package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.ExchangeRateApiResponse;
import com.example.expensely_backend.model.ExchangeRate;
import com.example.expensely_backend.repository.ExchangeRateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
								apiRate.getRate()
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
}
