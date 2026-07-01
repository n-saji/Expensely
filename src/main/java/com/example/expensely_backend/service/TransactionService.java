package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.*;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.*;
import com.example.expensely_backend.repository.*;
import com.example.expensely_backend.utils.FormatDate;
import com.example.expensely_backend.utils.S3Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionService {

	private final TransactionRepository transactionRepository;
	private final CategoryService categoryService;
	private final UserService userService;
	private final BudgetService budgetService;
	private final TransactionRepositoryCustomImpl transactionRepositoryCustomImpl;
	private final TransactionFilesRepository transactionFilesRepository;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;
	private final CategoryRepository categoryRepository;
	private final DbLogService dbLogService;
	private final ExchangeRateService exchangeRateService;

	@Autowired
	private S3Service s3Service;

	public TransactionService(TransactionRepository transactionRepository, CategoryService categoryService,
	                          UserService userService, BudgetService budgetService,
	                          TransactionRepositoryCustomImpl transactionRepositoryCustomImpl,
	                          TransactionFilesRepository transactionFilesRepository, UserRepository userRepository,
	                          ObjectMapper objectMapper, CategoryRepository categoryRepository,
	                          DbLogService dbLogService, ExchangeRateService exchangeRateService) {
		this.transactionRepository = transactionRepository;
		this.categoryService = categoryService;
		this.userService = userService;
		this.budgetService = budgetService;
		this.transactionRepositoryCustomImpl = transactionRepositoryCustomImpl;
		this.transactionFilesRepository = transactionFilesRepository;
		this.userRepository = userRepository;
		this.objectMapper = objectMapper;
		this.categoryRepository = categoryRepository;
		this.dbLogService = dbLogService;
		this.exchangeRateService = exchangeRateService;
	}

	private BigDecimal normalizeAmount(BigDecimal amount) {
		return exchangeRateService.normalizeDisplayAmount(amount);
	}

	private BigDecimal resolveUsdRate(String currency) {
		return exchangeRateService.getUsdToCurrencyRate(currency);
	}

	private void applyCurrencySnapshot(Transaction transaction, String currency) {
		transaction.setAmount(normalizeAmount(transaction.getAmount()));
		transaction.setCurrency(currency.toUpperCase());
		transaction.setBaseCurrency(globals.BASE_CURRENCY);
		BigDecimal rate = resolveUsdRate(currency);
		transaction.setExchangeRate(rate);
		transaction.setBaseCurrencyAmount(exchangeRateService.convertToUsd(transaction.getAmount(), currency));
	}

	private List<TransactionResponse> mapTransactionsToResponse(List<Transaction> transactions, String displayCurrency) {
		BigDecimal displayRate = resolveUsdRate(displayCurrency);
		return transactions.stream()
				.map(t -> {
					BigDecimal displayAmount = t.getBaseCurrencyAmount() == null
							? normalizeAmount(t.getAmount())
							: t.getBaseCurrencyAmount().multiply(displayRate).setScale(2, java.math.RoundingMode.HALF_UP);
					return new TransactionResponse(t, displayCurrency, displayAmount);
				})
				.collect(Collectors.toList());
	}

	private TransactionResponse mapTransactionToResponse(Transaction transaction, String displayCurrency) {
		BigDecimal displayRate = resolveUsdRate(displayCurrency);
		BigDecimal displayAmount = transaction.getBaseCurrencyAmount() == null
				? normalizeAmount(transaction.getAmount())
				: transaction.getBaseCurrencyAmount().multiply(displayRate).setScale(2, java.math.RoundingMode.HALF_UP);
		return new TransactionResponse(transaction, displayCurrency, displayAmount);
	}

	public Transaction save(Transaction transaction) {
		User user = userService.GetActiveUserById(transaction.getUser().getId().toString());
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		transaction.setUser(user);

		Category category = categoryService.getCategoryById(transaction.getCategory().getId().toString());
		if (category == null) {
			throw new IllegalArgumentException("Category not found");
		}
		transaction.setCategory(category);

		applyCurrencySnapshot(transaction, user.getCurrency() != null ? user.getCurrency() : globals.BASE_CURRENCY);

		Transaction saved = transactionRepository.save(transaction);

		if (saved.getType() == TransactionType.EXPENSE) {
			try {
				budgetService.updateBudgetAmountByUserIdAndCategoryId(user.getId().toString(), category.getId().toString(), saved.getBaseCurrencyAmount(), saved.getTransactionDate());
			} catch (Exception e) {
				dbLogService.logError("service", getClass().getName(), "save", "Budget update failed for expense creation: " + e.getMessage(), e);
			}
		}

		return saved;
	}

	public Transaction getTransactionById(String id) {
		return transactionRepository.findById(UUID.fromString(id)).orElse(null);
	}

	public TransactionResponse getTransactionResponseById(String id) {
		Transaction t = getTransactionById(id);
		if (t == null) {
			throw new IllegalArgumentException("Transaction not found");
		}
		return mapTransactionToResponse(t, t.getUser().getCurrency());
	}

	public void deleteTransactionById(String id) {
		Transaction t = getTransactionById(id);
		if (t == null) {
			throw new IllegalArgumentException("Transaction not found");
		}

		transactionRepository.deleteById(UUID.fromString(id));

		if (t.getType() == TransactionType.EXPENSE) {
			try {
				budgetService.updateBudgetAmountByUserIdAndCategoryId(t.getUser().getId().toString(), t.getCategory().getId().toString(), t.getBaseCurrencyAmount().negate(), t.getTransactionDate());
			} catch (Exception e) {
				dbLogService.logError("service", getClass().getName(), "deleteTransactionById", "Budget update failed for expense deletion: " + e.getMessage(), e);
			}
		}
	}

	@Transactional
	public void deleteByUserIdAndTransactionIds(String userId, List<Transaction> transactions) {
		UUID userUUID = UUID.fromString(userId);
		for (Transaction t : transactions) {
			Transaction dbT = transactionRepository.findByUserIdAndIdAndType(userUUID, t.getId(), t.getType());
			if (dbT != null) {
				transactionRepository.delete(dbT);
				if (dbT.getType() == TransactionType.EXPENSE) {
					try {
						budgetService.updateBudgetAmountByUserIdAndCategoryId(userId, dbT.getCategory().getId().toString(), dbT.getBaseCurrencyAmount().negate(), dbT.getTransactionDate());
					} catch (Exception e) {
						dbLogService.logError("service", getClass().getName(), "deleteByUserIdAndTransactionIds", "Budget update failed for bulk delete: " + e.getMessage(), e);
					}
				}
			}
		}
	}

	public Transaction updateTransaction(Transaction transaction) {
		Transaction oldT = getTransactionById(transaction.getId().toString());
		if (oldT == null) {
			throw new IllegalArgumentException("Transaction not found");
		}

		BigDecimal previousBaseAmount = oldT.getBaseCurrencyAmount();

		if (transaction.getCategory() != null && transaction.getCategory().getId() != null && !transaction.getCategory().getId().equals(oldT.getCategory().getId())) {
			Category category = categoryService.getCategoryById(transaction.getCategory().getId().toString());
			if (category == null) {
				throw new IllegalArgumentException("Category not found");
			}

			if (oldT.getType() == TransactionType.EXPENSE) {
				try {
					budgetService.updateBudgetAmountByUserIdAndCategoryId(oldT.getUser().getId().toString(), oldT.getCategory().getId().toString(), oldT.getBaseCurrencyAmount().negate(), oldT.getTransactionDate());
				} catch (Exception e) {
					dbLogService.logError("service", getClass().getName(), "updateTransaction", "Old budget update failed: " + e.getMessage(), e);
				}
			}
			oldT.setCategory(category);
		}

		if (transaction.getCurrency() != null && !transaction.getCurrency().equalsIgnoreCase(oldT.getCurrency())) {
			applyCurrencySnapshot(oldT, transaction.getCurrency());
		}

		if (transaction.getAmount() != null && transaction.getAmount().compareTo(oldT.getAmount()) != 0) {
			oldT.setAmount(normalizeAmount(transaction.getAmount()));
			String currency = oldT.getCurrency() == null ? globals.BASE_CURRENCY : oldT.getCurrency();
			BigDecimal rate = oldT.getExchangeRate();
			if (rate == null) {
				rate = resolveUsdRate(currency);
			}
			if (globals.BASE_CURRENCY.equalsIgnoreCase(currency)) {
				oldT.setBaseCurrencyAmount(oldT.getAmount().setScale(2, java.math.RoundingMode.HALF_UP));
			} else {
				oldT.setBaseCurrencyAmount(oldT.getAmount()
						.divide(rate, 8, java.math.RoundingMode.HALF_UP)
						.setScale(2, java.math.RoundingMode.HALF_UP));
			}
		}

		if (transaction.getDescription() != null && !transaction.getDescription().equals(oldT.getDescription())) {
			oldT.setDescription(transaction.getDescription());
		}
		if (transaction.getTransactionDate() != null && !transaction.getTransactionDate().equals(oldT.getTransactionDate())) {
			oldT.setTransactionDate(transaction.getTransactionDate());
		}

		Transaction updated = transactionRepository.save(oldT);

		if (updated.getType() == TransactionType.EXPENSE) {
			BigDecimal diff = updated.getBaseCurrencyAmount().subtract(previousBaseAmount);
			try {
				budgetService.updateBudgetAmountByUserIdAndCategoryId(updated.getUser().getId().toString(), updated.getCategory().getId().toString(), diff, updated.getTransactionDate());
			} catch (Exception e) {
				dbLogService.logError("service", getClass().getName(), "updateTransaction", "New budget update failed: " + e.getMessage(), e);
			}
		}

		return updated;
	}

	public TransactionResList fetchTransactionsWithConditions(
			String userIdStr, LocalDateTime startDate, LocalDateTime endDate, String order,
			String categoryId, int page, int limit, String q, String customSortBy, String customSortOrder, String typeStr
	) {
		if (page < 1) {
			throw new IllegalArgumentException("Page must be greater than 0");
		}
		int offset = (page - 1) * limit;
		UUID userUUID = UUID.fromString(userIdStr);

		UUID categoryUUID = null;
		if (categoryId != null && !categoryId.trim().isEmpty()) {
			categoryUUID = UUID.fromString(categoryId);
		}

		TransactionType type = null;
		if (typeStr != null && !typeStr.trim().isEmpty()) {
			type = TransactionType.valueOf(typeStr.trim().toUpperCase());
		}

		List<Transaction> transactions = transactionRepositoryCustomImpl.findTransactions(
				userUUID, type, startDate, endDate, categoryUUID, q, offset, limit, customSortBy, customSortOrder, order
		);

		long totalElements = transactionRepositoryCustomImpl.countTransactions(
				userUUID, type, startDate, endDate, categoryUUID, q
		);

		long totalPages = (long) Math.ceil((double) totalElements / limit);

		User user = userService.GetActiveUserById(userIdStr);
		String displayCurrency = user == null ? globals.BASE_CURRENCY : user.getCurrency();

		return new TransactionResList(mapTransactionsToResponse(transactions, displayCurrency), totalPages, totalElements, page);
	}

	public String exportTransactionsToCSV(String userId, LocalDateTime startDate, LocalDateTime endDate, String typeStr) {
		UUID userUUID = UUID.fromString(userId);
		TransactionType type = null;
		if (typeStr != null && !typeStr.trim().isEmpty()) {
			type = TransactionType.valueOf(typeStr.trim().toUpperCase());
		}

		List<Transaction> transactions = transactionRepositoryCustomImpl.findTransactions(
				userUUID, type, startDate, endDate, null, "", 0, Integer.MAX_VALUE, "transactionDate", "desc", "desc"
		);

		User user = userService.GetActiveUserById(userId);
		String displayCurrency = user == null ? globals.BASE_CURRENCY : user.getCurrency();
		BigDecimal displayRate = resolveUsdRate(displayCurrency);

		StringWriter sw = new StringWriter();
		CSVWriter writer = new CSVWriter(sw);
		writer.writeNext(new String[]{"ID", "Amount", "Currency", "Date", "Description", "Category", "Type"});

		for (Transaction t : transactions) {
			BigDecimal displayAmount = t.getBaseCurrencyAmount() == null
					? normalizeAmount(t.getAmount())
					: t.getBaseCurrencyAmount().multiply(displayRate).setScale(2, java.math.RoundingMode.HALF_UP);

			writer.writeNext(new String[]{
					t.getId().toString(),
					displayAmount.toString(),
					displayCurrency,
					t.getTransactionDate().toString(),
					t.getDescription(),
					t.getCategory().getName(),
					t.getType().name()
			});
		}
		try {
			writer.close();
		} catch (IOException e) {
			dbLogService.logError("service", getClass().getName(), "exportTransactionsToCSV", e.getMessage(), e);
		}
		return sw.toString();
	}

	public String BulkInsertTransactionsFromFile(String userId, String fileId) {
		UUID fileUUID = UUID.fromString(fileId);
		UUID userUUID = UUID.fromString(userId);

		TransactionFiles tf = transactionFilesRepository.findById(fileUUID)
				.orElseThrow(() -> new IllegalArgumentException("File not found"));

		User user = userRepository.findById(userUUID)
				.orElseThrow(() -> new IllegalArgumentException("User not found"));

		if (!tf.getUserId().equals(userId)) {
			throw new SecurityException("Unauthorized access to file");
		}

		if (tf.getExpiresAt() < System.currentTimeMillis()) {
			throw new IllegalStateException("File validation expired");
		}

		List<TransactionUploadDto> rows;
		try {
			rows = objectMapper.readValue(
					tf.getTransactions(),
					new TypeReference<>() {}
			);
		} catch (Exception e) {
			dbLogService.logError("service", getClass().getName(), "BulkInsertTransactionsFromFile",
					tf.getTransactions() + ": " + e.getMessage(), e);
			throw new RuntimeException("Failed to parse transaction data", e);
		}

		List<Category> cats = categoryRepository.findByUserId(userUUID);
		LinkedHashMap<String, Category> catsList = new LinkedHashMap<>();
		cats.forEach(cat -> catsList.put(cat.getName(), cat));

		List<Transaction> transactions = rows.stream().map(dto -> {
			Transaction transaction = new Transaction();
			transaction.setAmount(normalizeAmount(BigDecimal.valueOf(dto.getAmount())));
			transaction.setCategory(catsList.get(dto.getCategory()));
			transaction.setTransactionDate(dto.getTransaction_date().atStartOfDay());
			transaction.setDescription(dto.getDescription());
			transaction.setUser(user);
			transaction.setType(TransactionType.valueOf(dto.getType().toUpperCase()));
			applyCurrencySnapshot(transaction, user.getCurrency() != null ? user.getCurrency() : globals.BASE_CURRENCY);
			return transaction;
		}).toList();

		transactionRepository.saveAll(transactions);

		// Update budgets for expenses inserted
		for (Transaction saved : transactions) {
			if (saved.getType() == TransactionType.EXPENSE) {
				try {
					budgetService.updateBudgetAmountByUserIdAndCategoryId(userId, saved.getCategory().getId().toString(), saved.getBaseCurrencyAmount(), saved.getTransactionDate());
				} catch (Exception e) {
					dbLogService.logError("service", getClass().getName(), "BulkInsertTransactionsFromFile", "Budget update failed: " + e.getMessage(), e);
				}
			}
		}

		transactionFilesRepository.deleteById(fileUUID);
		return "Transactions inserted successfully";
	}

	public Map<String, String> GenerateS3PresignedURLForTransaction(String userId, String fileName, String contentType) {
		String key = userId + "/" + fileName;
		try {
			return Map.of("presignedURL", s3Service.generatePresignedURL(key, contentType), "fileURL", key);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void UpdateTransactionAttachment(String userId, String transactionId, String key) {
		try {
			Transaction transaction = transactionRepository.findByUserIdAndIdAndType(
					UUID.fromString(userId), UUID.fromString(transactionId), TransactionType.EXPENSE
			);
			if (transaction == null) {
				throw new IllegalArgumentException("Transaction not found or not an expense");
			}
			transaction.setReceiptUrl(key);
			transactionRepository.save(transaction);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String GenerateDownloadURLForTransaction(String tid) {
		try {
			Optional<Transaction> transaction = transactionRepository.findById(UUID.fromString(tid));
			if (transaction.isEmpty()) {
				throw new IllegalArgumentException("Transaction not found");
			}
			if (transaction.get().getReceiptUrl() == null) {
				throw new IllegalArgumentException("Receipt URL not found");
			}
			return s3Service.generateDownloadUrl(transaction.get().getReceiptUrl());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void DeleteTransactionAttachment(String userId, String transactionId) {
		try {
			Transaction transaction = transactionRepository.findByUserIdAndIdAndType(
					UUID.fromString(userId), UUID.fromString(transactionId), TransactionType.EXPENSE
			);
			if (transaction == null) {
				throw new IllegalArgumentException("Transaction not found or not an expense");
			}
			String receiptUrl = transaction.getReceiptUrl();
			if (receiptUrl == null || receiptUrl.isBlank()) {
				throw new IllegalArgumentException("Receipt URL not found");
			}
			s3Service.deleteObject(receiptUrl);
			transaction.setReceiptUrl(null);
			transactionRepository.save(transaction);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
