package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate,
		Long> {
	Optional<ExchangeRate> findByBaseCurrencyAndTargetCurrency(String baseCurrency,
	                                                           String targetCurrency);

	List<ExchangeRate> findByBaseCurrencyIgnoreCaseOrderByTargetCurrencyAsc(String baseCurrency);

	List<ExchangeRate> findByBaseCurrencyIgnoreCaseAndTargetCurrencyContainingIgnoreCaseOrderByTargetCurrencyAsc(
			String baseCurrency,
			String targetCurrency
	);

	@Query(
			value = "select distinct base_currency as currency from exchange_rates "
					+ "union select distinct target_currency as currency from exchange_rates "
					+ "order by currency",
			nativeQuery = true
	)
	List<String> findDistinctCurrencies();
}
