package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.RecurringExpenseDTO;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.model.TransactionType;
import com.example.expensely_backend.model.RecurringExpense;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.CategoryRepository;
import com.example.expensely_backend.repository.TransactionRepository;
import com.example.expensely_backend.repository.RecurringExpenseRepository;
import com.example.expensely_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RecurringExpenseService {
	private static final String BASE_CURRENCY = "USD";
	private final RecurringExpenseRepository recurringExpenseRepository;
	private final UserRepository userRepository;
	private final CategoryRepository categoryRepository;
	private final TransactionRepository transactionRepository;
	private final ExchangeRateService exchangeRateService;

	public RecurringExpenseService(RecurringExpenseRepository recurringExpenseRepository, UserRepository userRepository, CategoryRepository categoryRepository, TransactionRepository transactionRepository, ExchangeRateService exchangeRateService) {
		this.userRepository = userRepository;
		this.categoryRepository = categoryRepository;
		this.recurringExpenseRepository = recurringExpenseRepository;
		this.transactionRepository = transactionRepository;
		this.exchangeRateService = exchangeRateService;
	}

	public void createRecurringExpense(RecurringExpenseDTO recExpenseDTO) {
		// Logic to create a recurring expense
		// validate input
		String user_id = recExpenseDTO.getUserId();
		UUID user_id_uuid = UUID.fromString(user_id);
		if (!userRepository.existsById(user_id_uuid)) {
			throw new IllegalArgumentException("User not found");
		}
		User usr = userRepository.getReferenceById(user_id_uuid);
		String category_id = recExpenseDTO.getCategoryId();
		UUID category_id_uuid = UUID.fromString(category_id);
		if (!categoryRepository.existsById(category_id_uuid)) {
			throw new IllegalArgumentException("Category not found");
		}
		Category category = categoryRepository.getReferenceById(category_id_uuid);
		RecurringExpense recurringExpense = new RecurringExpense();
		recurringExpense.setUser(usr);
		recurringExpense.setCategory(category);
		recurringExpense.setAmount(exchangeRateService.normalizeDisplayAmount(recExpenseDTO.getAmount()));
		String currency = recExpenseDTO.getCurrency();
		if (currency == null || currency.isBlank()) {
			currency = BASE_CURRENCY;
		}
		recurringExpense.setCurrency(currency.toUpperCase());
		recurringExpense.setDescription(recExpenseDTO.getDescription());
		recurringExpense.setRecurrence(recExpenseDTO.getRecurrence());
		recurringExpense.setActive(true);
		recurringExpense.setDate(recExpenseDTO.getDate());
		LocalDate localDate = recExpenseDTO.getDate();

		if (localDate.isBefore(LocalDate.now())) {
			throw new IllegalArgumentException("Date cannot be in the past");
		}

		if (recurringExpense.getDate().isEqual(LocalDate.now())) {
			try {
				Transaction expense = new Transaction();
				expense.setUser(usr);
				expense.setCategory(category);
				expense.setAmount(exchangeRateService.normalizeDisplayAmount(recExpenseDTO.getAmount()));
				expense.setCurrency(recurringExpense.getCurrency());
				expense.setBaseCurrency(BASE_CURRENCY);
				expense.setExchangeRate(exchangeRateService.getUsdToCurrencyRate(recurringExpense.getCurrency()));
				expense.setBaseCurrencyAmount(exchangeRateService.convertToUsd(recExpenseDTO.getAmount(), recurringExpense.getCurrency()));
				expense.setDescription(recExpenseDTO.getDescription());
				expense.setTransactionDate(LocalDateTime.now());
				expense.setType(TransactionType.EXPENSE);
				transactionRepository.save(expense);
			} catch (Exception e) {
				throw new RuntimeException("Failed to create initial expense for recurring expense: " + e.getMessage());
			}

			switch (recExpenseDTO.getRecurrence()) {
				case DAILY ->
						recurringExpense.setNextOccurrence(localDate.plusDays(1));
				case WEEKLY ->
						recurringExpense.setNextOccurrence(localDate.plusWeeks(1));
				case MONTHLY ->
						recurringExpense.setNextOccurrence(localDate.plusMonths(1));
				case YEARLY ->
						recurringExpense.setNextOccurrence(localDate.plusYears(1));
				default ->
						throw new IllegalArgumentException("Invalid recurrence type");
			}
		} else {
			recurringExpense.setNextOccurrence(localDate);
		}

		try {
			recurringExpenseRepository.save(recurringExpense);
		} catch (Exception e) {
			throw new RuntimeException("Failed to create recurring expense: " + e.getMessage());
		}
	}

	public void deleteRecurringExpenseById(String id) {
		UUID uuid = UUID.fromString(id);
		if (!recurringExpenseRepository.existsById(uuid)) {
			throw new IllegalArgumentException("Recurring expense not found");
		}
		try {
			recurringExpenseRepository.deleteById(uuid);
		} catch (Exception e) {
			throw new RuntimeException("Failed to delete recurring expense: " + e.getMessage());
		}
	}

	public RecurringExpenseDTO findRecurringExpenseById(String id) {
		UUID uuid = UUID.fromString(id);
		RecurringExpense rEM =
				recurringExpenseRepository.findById(uuid).orElseThrow(() -> new IllegalArgumentException("Recurring expense not found"));
		return new RecurringExpenseDTO(
				rEM.getId().toString(),
				rEM.getUser().getId().toString(),
				rEM.getCategory().getId().toString(),
				rEM.getAmount(),
				rEM.getCurrency(),
				rEM.getDescription(),
				rEM.getRecurrence(),
				rEM.getDate(),
				rEM.getNextOccurrence(),
				rEM.isActive()
		);
	}

	public void updateRecurringExpense(String id,
	                                   RecurringExpenseDTO recExpenseDTO) {
		UUID uuid = UUID.fromString(id);
		RecurringExpense existingExpense =
				recurringExpenseRepository.findById(uuid).orElseThrow(() -> new IllegalArgumentException("Recurring expense not found"));
		if (recExpenseDTO.getAmount() != null) {
			existingExpense.setAmount(exchangeRateService.normalizeDisplayAmount(recExpenseDTO.getAmount()));
		}
		if (recExpenseDTO.getCurrency() != null && !recExpenseDTO.getCurrency().isBlank()) {
			existingExpense.setCurrency(recExpenseDTO.getCurrency().toUpperCase());
		}
		if (recExpenseDTO.getDescription() != null) {
			existingExpense.setDescription(recExpenseDTO.getDescription());
		}

		if (recExpenseDTO.getDate() != null && !recExpenseDTO.getDate().isEqual(existingExpense.getDate())) {
			if (recExpenseDTO.getDate().isBefore(LocalDate.now())) {
				throw new IllegalArgumentException("Date cannot be in the past");
			}
			existingExpense.setDate(recExpenseDTO.getDate());

			if (existingExpense.getDate().isEqual(LocalDate.now())) {
				try {
					Transaction expense = new Transaction();
					expense.setUser(existingExpense.getUser());
					expense.setCategory(existingExpense.getCategory());
					expense.setAmount(exchangeRateService.normalizeDisplayAmount(existingExpense.getAmount()));
					expense.setCurrency(existingExpense.getCurrency());
					expense.setBaseCurrency(BASE_CURRENCY);
					expense.setExchangeRate(exchangeRateService.getUsdToCurrencyRate(existingExpense.getCurrency()));
					expense.setBaseCurrencyAmount(exchangeRateService.convertToUsd(existingExpense.getAmount(), existingExpense.getCurrency()));
					expense.setDescription(existingExpense.getDescription());
					expense.setTransactionDate(LocalDateTime.now());
					expense.setType(TransactionType.EXPENSE);
					transactionRepository.save(expense);
				} catch (Exception e) {
					throw new RuntimeException("Failed to create initial expense for recurring expense: " + e.getMessage());
				}


				switch (recExpenseDTO.getRecurrence()) {
					case DAILY ->
							existingExpense.setNextOccurrence(existingExpense.getDate().plusDays(1));
					case WEEKLY ->
							existingExpense.setNextOccurrence(existingExpense.getDate().plusWeeks(1));
					case MONTHLY ->
							existingExpense.setNextOccurrence(existingExpense.getDate().plusMonths(1));
					case YEARLY ->
							existingExpense.setNextOccurrence(existingExpense.getDate().plusYears(1));
					default ->
							throw new IllegalArgumentException("Invalid recurrence type");
				}
			} else {
				existingExpense.setNextOccurrence(recExpenseDTO.getDate());
			}
		}
		try {
			recurringExpenseRepository.save(existingExpense);
		} catch (Exception e) {
			throw new RuntimeException("Failed to update recurring expense: " + e.getMessage());
		}
	}

	public void deactivateRecurringExpense(String id) {
		UUID uuid = UUID.fromString(id);
		RecurringExpense existingExpense =
				recurringExpenseRepository.findById(uuid).orElseThrow(() -> new IllegalArgumentException("Recurring expense not found"));
		existingExpense.setActive(false);
		try {
			recurringExpenseRepository.save(existingExpense);
		} catch (Exception e) {
			throw new RuntimeException("Failed to deactivate recurring expense: " + e.getMessage());
		}
	}

	public void activateRecurringExpense(String id) {
		UUID uuid = UUID.fromString(id);
		RecurringExpense existingExpense =
				recurringExpenseRepository.findById(uuid).orElseThrow(() -> new IllegalArgumentException("Recurring expense not found"));
		existingExpense.setActive(true);
		try {
			recurringExpenseRepository.save(existingExpense);
		} catch (Exception e) {
			throw new RuntimeException("Failed to activate recurring expense: " + e.getMessage());
		}
	}

	public List<RecurringExpenseDTO> findRecurringExpenseByUserId(String userId) {
		UUID uuid = UUID.fromString(userId);

		return recurringExpenseRepository.findByUserIdOrderByCreatedAtDesc(uuid).stream().map(expense -> new RecurringExpenseDTO(
				expense.getId().toString(),
				expense.getUser().getId().toString(),
				expense.getCategory().getId().toString(),
				expense.getAmount(),
				expense.getCurrency(),
				expense.getDescription(),
				expense.getRecurrence(),
				expense.getDate(),
				expense.getNextOccurrence(),
				expense.isActive()
		)).toList();
	}
}

