package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.ExchangeRateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExchangeRateHistoryRepository extends JpaRepository<ExchangeRateHistory, Long> {

	List<ExchangeRateHistory> findByBaseCurrencyIgnoreCaseAndTargetCurrencyIgnoreCaseAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
			String baseCurrency,
			String targetCurrency,
			LocalDateTime since
	);

	List<ExchangeRateHistory> findByBaseCurrencyIgnoreCaseAndTargetCurrencyIgnoreCaseOrderByRecordedAtAsc(
			String baseCurrency,
			String targetCurrency
	);
}
