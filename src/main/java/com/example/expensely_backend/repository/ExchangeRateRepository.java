package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate,
		Long> {
	Optional<ExchangeRate> findByBaseCurrencyAndTargetCurrency(String baseCurrency,
	                                                           String targetCurrency);
}
