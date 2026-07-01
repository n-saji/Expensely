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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

	private final TransactionRepository transactionRepository;
	private final CategoryService categoryService;
	private final UserService userService;
	private final BudgetService budgetService;
	private final TransactionRepositoryCustomImpl transactionRepositoryCustomImpl;
	private final ExpenseFilesRepository expenseFilesRepository;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;
	private final CategoryRepository categoryRepository;
	private final DbLogService dbLogService;
	private final RecurringExpenseService recurringExpenseService;
	private final Executor expenseExecutor;
	private final ExchangeRateService exchangeRateService;

	@Autowired
	private S3Service s3Service;

	public ExpenseService(TransactionRepository transactionRepository, CategoryService categoryService,
	                      UserService userService,
	                      BudgetService budgetService, TransactionRepositoryCustomImpl transactionRepositoryCustomImpl
			, ExpenseFilesRepository expenseFilesService, UserRepository userRepository,
			              ObjectMapper objectMapper,
			              CategoryRepository categoryRepository,
			              DbLogService dbLogService,
			              RecurringExpenseService recurringExpenseService,
			              @Qualifier("expenseExecutor") Executor expenseExecutor,
			              ExchangeRateService exchangeRateService) {
		this.transactionRepository = transactionRepository;
		this.categoryService = categoryService;
		this.userService = userService;
		this.budgetService = budgetService;
		this.transactionRepositoryCustomImpl = transactionRepositoryCustomImpl;
		this.expenseFilesRepository = expenseFilesService;
		this.userRepository = userRepository;
		this.objectMapper = objectMapper;
		this.categoryRepository = categoryRepository;
		this.dbLogService = dbLogService;
		this.recurringExpenseService = recurringExpenseService;
		this.expenseExecutor = expenseExecutor;
		this.exchangeRateService = exchangeRateService;

	}

	private BigDecimal normalizeAmount(BigDecimal amount) {
		return exchangeRateService.normalizeDisplayAmount(amount);
	}

	private BigDecimal resolveUsdRate(String currency) {
		return exchangeRateService.getUsdToCurrencyRate(currency);
	}

	private void applyCurrencySnapshot(Transaction expense, String currency) {
		expense.setAmount(normalizeAmount(expense.getAmount()));
		expense.setCurrency(currency.toUpperCase());
		expense.setBaseCurrency(globals.BASE_CURRENCY);
		BigDecimal rate = resolveUsdRate(currency);
		expense.setExchangeRate(rate);
		expense.setBaseCurrencyAmount(exchangeRateService.convertToUsd(expense.getAmount(), currency));
	}

	private List<ExpenseResponse> mapExpensesToResponse(List<Transaction> expenses, String displayCurrency) {
		BigDecimal displayRate = resolveUsdRate(displayCurrency);
		return expenses.stream()
				.map(expense -> {
					BigDecimal displayAmount = expense.getBaseCurrencyAmount() == null
							? normalizeAmount(expense.getAmount())
							: expense.getBaseCurrencyAmount().multiply(displayRate).setScale(2, java.math.RoundingMode.HALF_UP);
					return new ExpenseResponse(expense, displayCurrency, displayAmount);
				})
				.collect(Collectors.toList());
	}

	private ExpenseResponse mapExpenseToResponse(Transaction expense, String displayCurrency) {
		BigDecimal displayRate = resolveUsdRate(displayCurrency);
		BigDecimal displayAmount = expense.getBaseCurrencyAmount() == null
				? normalizeAmount(expense.getAmount())
				: expense.getBaseCurrencyAmount().multiply(displayRate).setScale(2, java.math.RoundingMode.HALF_UP);
		return new ExpenseResponse(expense, displayCurrency, displayAmount);
	}

	private UUID getActiveUserIdOrThrow(String userId) {
		return userService.GetActiveUserById(userId).getId();
	}

	private List<ExpenseResponse> getExpenseByUserIdAndStartDateAndEndDate(UUID userId, LocalDateTime startDate, LocalDateTime endDate, String order) {
		List<Transaction> expenses;
		if (order == null || order.equalsIgnoreCase("desc")) {
			expenses = transactionRepository.findByUserIdAndTypeAndTimeFrameDesc(userId, TransactionType.EXPENSE, startDate, endDate);
		} else if (order.equalsIgnoreCase("asc")) {
			expenses = transactionRepository.findByUserIdAndTypeAndTimeFrameAsc(userId, TransactionType.EXPENSE, startDate, endDate);

		} else {
			throw new IllegalArgumentException("Order must be 'asc' or 'desc'");
		}

		User user = userService.GetActiveUserById(userId.toString());
		String displayCurrency = user == null ? globals.BASE_CURRENCY : user.getCurrency();
		return mapExpensesToResponse(expenses, displayCurrency);
	}

	private List<MonthlyCategoryExpense> getMonthlyCategoryExpense(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
		return transactionRepository.findMonthlyCategoryExpenseByUserId(userId, startDate, endDate);
	}

	private List<DailyExpense> getDailyExpense(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
		return transactionRepository.findDailyExpenseByUserIdAndTimeFrame(userId, startDate, endDate);
	}

	private ExpenseResList fetchExpensesWithConditions(UUID userId, LocalDateTime startDate,
	                                                   LocalDateTime endDate, String order,
	                                                   String categoryId, int page, int limit,
	                                                   String q, String customSortBy, String customSortOrder) {
		long totalPages;
		if (page < 1) {
			throw new IllegalArgumentException("Page must be greater than 0");
		}
		int offset = (page - 1) * limit;

		UUID categoryUUID = null;
		if (q == null) q = "";
		if (order == null) order = "desc";
		else order = order.toLowerCase();

		if (categoryId != null) {
			Category category = categoryService.getCategoryById(categoryId);
			if (category == null) {
				throw new IllegalArgumentException("Category not found");
			}
			categoryUUID = category.getId();
		}

		List<Transaction> expenses = transactionRepositoryCustomImpl.findTransactions(userId, TransactionType.EXPENSE, startDate, endDate,
				categoryUUID, q, offset, limit, customSortBy, customSortOrder, order);
		long totalElements = transactionRepositoryCustomImpl.countTransactions(userId, TransactionType.EXPENSE, startDate, endDate,
				categoryUUID, q);

		totalPages = (int) Math.ceil((double) totalElements / limit);
		User user = userService.GetActiveUserById(userId.toString());
		String displayCurrency = user == null ? globals.BASE_CURRENCY : user.getCurrency();
		return new ExpenseResList(mapExpensesToResponse(expenses, displayCurrency), totalPages, totalElements, page);
	}

	private Double getTotalExpenseForMonth(int year, int month, UUID userId) {
		LocalDateTime startDate = FormatDate.formatStartDate(LocalDateTime.of(year, month, 1, 0, 0), false);
		YearMonth reqYm = YearMonth.of(year, month);
		LocalDateTime endDate = reqYm.atEndOfMonth().atTime(23, 59, 59);

		return transactionRepository.getTotalAmountByUserId(userId, "EXPENSE", startDate, endDate);
	}

	private DateRange resolveDateRange(int count, globals.TimeFrame type) {
		if (type == null) {
			throw new IllegalArgumentException("Type not allowed to be null");
		}
		if (count < 1) {
			throw new IllegalArgumentException("Count must be greater than 0");
		}
		if (type == globals.TimeFrame.MONTH && count > 12) {
			throw new IllegalArgumentException("Count must be less than or equal to 12 for MONTH type");
		}

		LocalDateTime date = LocalDateTime.now(ZoneOffset.UTC);
		LocalDateTime startDate;
		switch (type) {
			case YEAR ->
					startDate = LocalDateTime.of(date.getYear() - count, date.getMonth(), 1, 0, 0, 0);
			case ALL_TIME -> startDate = date.minusYears(count - 1)
					.withMonth(1)
					.withDayOfMonth(1)
					.withHour(0)
					.withMinute(0)
					.withSecond(0)
					.withNano(0);
			case MONTH -> startDate = date.minusMonths(count - 1)
					.withDayOfMonth(1)
					.withHour(0)
					.withMinute(0)
					.withSecond(0)
					.withNano(0);
			default ->
					throw new IllegalArgumentException("Invalid time frame type");
		}

		YearMonth dateYm = YearMonth.of(date.getYear(), date.getMonth());
		LocalDateTime endDate = LocalDateTime.of(date.getYear(), date.getMonth(), dateYm.lengthOfMonth(), 23, 59, 59);

		return new DateRange(startDate, endDate);
	}

	public Transaction save(Transaction expense) {
		if (expense.getCategory() == null || expense.getCategory().getId() == null) {
			throw new IllegalArgumentException("Category must be provided");
		}
		Category category = categoryService.getCategoryById(expense.getCategory().getId().toString());
		if (category == null) {
			throw new IllegalArgumentException("Category not found");
		}
		expense.setCategory(category);
		if (expense.getUser() == null || expense.getUser().getId() == null) {
			throw new IllegalArgumentException("User must be provided");
		}
		User user = userService.GetActiveUserById(expense.getUser().getId().toString());
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}

		expense.setUser(user);
		String currency = expense.getCurrency();
		if (currency == null || currency.isBlank()) {
			currency = user.getCurrency();
		}
		expense.setType(TransactionType.EXPENSE);
		applyCurrencySnapshot(expense, currency);
		Transaction exp = transactionRepository.save(expense);


		// calculate if budget set

		try {
			budgetService.updateBudgetAmountByUserIdAndCategoryId(user.getId().toString(), category.getId().toString(), expense.getBaseCurrencyAmount(), expense.getTransactionDate());
		} catch (Exception e) {
			throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
		}

		return exp;

	}

	public Transaction getExpenseById(String id) {
		return transactionRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("Expense not found"));
	}

	public ExpenseResponse getExpenseResponseById(String id) {
		Transaction expense = getExpenseById(id);
		String displayCurrency = expense.getUser() == null ? globals.BASE_CURRENCY : expense.getUser().getCurrency();
		return mapExpenseToResponse(expense, displayCurrency);
	}

	public void deleteExpenseById(String id) {
		try {
			if (!transactionRepository.existsById(UUID.fromString(id))) {
				throw new IllegalArgumentException("Expense not found");
			}
			//budget update
			Transaction expense = getExpenseById(id);

			transactionRepository.deleteById(UUID.fromString(id));

			try {
				budgetService.updateBudgetAmountByUserIdAndCategoryId(expense.getUser().getId().toString(), expense.getCategory().getId().toString(), expense.getBaseCurrencyAmount().negate(), expense.getTransactionDate());
			} catch (Exception e) {
				throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Error deleting expense: " + e.getMessage());
		}
	}

	public List<ExpenseResponse> getExpensesByUserId(String userId) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		return mapExpensesToResponse(transactionRepository.findByUserIdAndType(user.getId(), TransactionType.EXPENSE), user.getCurrency());
	}

	public List<ExpenseResponse> getExpensesByCategoryIdAndUserID(String categoryId, String userId) {
		Category category = categoryService.getCategoryById(categoryId);
		if (category == null) {
			throw new IllegalArgumentException("Category not found");
		}
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		return mapExpensesToResponse(transactionRepository.findByCategoryIdAndUserIdAndType(category.getId(), user.getId(), TransactionType.EXPENSE), user.getCurrency());
	}

	public Transaction updateExpense(Transaction expense) {

		Transaction oldExpense = getExpenseById(expense.getId().toString());
		if (oldExpense == null) {
			throw new IllegalArgumentException("Expense not found");
		}
		BigDecimal previousBaseAmount = oldExpense.getBaseCurrencyAmount();
		if (expense.getCategory() != null && expense.getCategory().getId() != null && !expense.getCategory().getId().equals(oldExpense.getCategory().getId())) {
			Category category = categoryService.getCategoryById(expense.getCategory().getId().toString());
			if (category == null) {
				throw new IllegalArgumentException("Category not found");
			}

			try {
				budgetService.updateBudgetAmountByUserIdAndCategoryId(oldExpense.getUser().getId().toString(), oldExpense.getCategory().getId().toString(), oldExpense.getBaseCurrencyAmount().negate(), oldExpense.getTransactionDate());
			} catch (Exception e) {
				throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
			}
			oldExpense.setCategory(category);
		}


		if (expense.getCurrency() != null && !expense.getCurrency().equalsIgnoreCase(oldExpense.getCurrency())) {
			applyCurrencySnapshot(oldExpense, expense.getCurrency());
		}

		if (expense.getAmount() != null && expense.getAmount().compareTo(oldExpense.getAmount()) != 0) {
			oldExpense.setAmount(normalizeAmount(expense.getAmount()));
			String currency = oldExpense.getCurrency() == null ? globals.BASE_CURRENCY : oldExpense.getCurrency();
			BigDecimal rate = oldExpense.getExchangeRate();
			if (rate == null) {
				rate = resolveUsdRate(currency);
			}
			if (globals.BASE_CURRENCY.equalsIgnoreCase(currency)) {
				oldExpense.setBaseCurrencyAmount(oldExpense.getAmount().setScale(2, java.math.RoundingMode.HALF_UP));
			} else {
				oldExpense.setBaseCurrencyAmount(oldExpense.getAmount()
						.divide(rate, 8, java.math.RoundingMode.HALF_UP)
						.setScale(2, java.math.RoundingMode.HALF_UP));
			}
		}

		if (expense.getDescription() != null && !expense.getDescription().equals(oldExpense.getDescription())) {
			oldExpense.setDescription(expense.getDescription());
		}
		if (expense.getTransactionDate() != null && !expense.getTransactionDate().equals(oldExpense.getTransactionDate())) {
			oldExpense.setTransactionDate(expense.getTransactionDate());
		}


		Transaction exp = transactionRepository.save(oldExpense);


//        update budget
		try {
			budgetService.updateBudgetAmountByUserIdAndCategoryId(exp.getUser().getId().toString(), exp.getCategory().getId().toString(), oldExpense.getBaseCurrencyAmount().subtract(previousBaseAmount), exp.getTransactionDate());
		} catch (Exception e) {
			throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
		}
		return exp;
	}

	public List<ExpenseResponse> getExpenseByUserIdAndStartDateAndEndDate(String userId, LocalDateTime startDate, LocalDateTime endDate, String order) {
		return getExpenseByUserIdAndStartDateAndEndDate(getActiveUserIdOrThrow(userId), startDate, endDate, order);
	}

	public void deleteBuUserIDAndExpenseIds(String userId, List<Transaction> expenses) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}

		for (int i = 0; i < expenses.size(); i++) {
			if (expenses.get(i).getId() == null) {
				throw new IllegalArgumentException("Expense ID must be provided");
			}
			expenses.set(i, getExpenseById(expenses.get(i).getId().toString()));
		}

		for (Transaction expense : expenses) {
			if (!expense.getUser().getId().equals(user.getId())) {
				throw new IllegalArgumentException("Expense does not belong to user");
			}
		}
		transactionRepository.deleteAll(expenses);
		for (Transaction expense : expenses) {
			try {
				budgetService.updateBudgetAmountByUserIdAndCategoryId(expense.getUser().getId().toString(), expense.getCategory().getId().toString(), expense.getBaseCurrencyAmount().negate(), expense.getTransactionDate());
			} catch (Exception e) {
				throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
			}
		}
	}

	public ExpenseResList fetchExpensesWithConditions(String userId, LocalDateTime startDate,
	                                                  LocalDateTime endDate, String order,
	                                                  String categoryId, int page, int limit,
	                                                  String q, String customSortBy, String customSortOrder) {
		return fetchExpensesWithConditions(getActiveUserIdOrThrow(userId), startDate, endDate, order,
				categoryId, page, limit, q, customSortBy, customSortOrder);
	}

	public String exportExpensesToCSV(String userId, LocalDateTime startDate, LocalDateTime endDate) throws IOException {
		UUID userIdUUID = UUID.fromString(userId);
		User user = userService.GetActiveUserById(userId);
		List<Transaction> expenses = transactionRepository.findByUserIdAndTypeAndTimeFrameAsc(userIdUUID, TransactionType.EXPENSE, startDate, endDate);
		String displayCurrency = user == null ? globals.BASE_CURRENCY : user.getCurrency();
		BigDecimal displayRate = resolveUsdRate(displayCurrency);

		StringWriter sw = new StringWriter();
		CSVWriter writer = new CSVWriter(sw);

		// header
		writer.writeNext(new String[]{"Date", "Description", "Amount (in " + displayCurrency + ")", "Category"});

		// rows
		for (Transaction expense : expenses) {
			BigDecimal displayAmount = expense.getBaseCurrencyAmount() == null
					? normalizeAmount(expense.getAmount())
					: expense.getBaseCurrencyAmount().multiply(displayRate).setScale(2, java.math.RoundingMode.HALF_UP);
			writer.writeNext(new String[]{
					expense.getTransactionDate().toString(),
					expense.getDescription(),
					displayAmount.toString(),
					expense.getCategory().getName()
			});
		}
		writer.close();
		return sw.toString();
	}

	public String BulkInsertExpensesFromFile(String userId, String fileId) {
		UUID fileUUID = UUID.fromString(fileId);
		UUID userUUID = UUID.fromString(userId);
		ExpenseFiles ef =
				expenseFilesRepository.findById(fileUUID).orElseThrow(() -> new IllegalArgumentException("File not found"));

		User user =
				userRepository.findById(userUUID).orElseThrow(() -> new IllegalArgumentException("User not found"));

		if (!ef.getUserId().equals(userId)) {
			throw new SecurityException("Unauthorized access to file");
		}

		if (ef.getExpiresAt() < System.currentTimeMillis()) {
			throw new IllegalStateException("File validation expired");
		}


		List<ExpenseUploadDto> rows;
		try {
			rows = objectMapper.readValue(
					ef.getExpenses(),
					new TypeReference<>() {
					}
			);
		} catch (Exception e) {
			dbLogService.logError("service", getClass().getName(), "BulkInsertExpensesFromFile",
					ef.getExpenses() + ": " + e.getMessage(), e);
			throw new RuntimeException("Failed to parse expense data", e);
		}

		List<Category> cats = categoryRepository.findByUserId(userUUID);
		LinkedHashMap<String, Category> catsList = new LinkedHashMap<>();
		cats.forEach(cat -> catsList.put(cat.getName(), cat));
		List<Transaction> expenses = rows.stream().map(dto -> {
			Transaction expense = new Transaction();
			expense.setAmount(normalizeAmount(BigDecimal.valueOf(dto.getAmount())));
			expense.setCategory(catsList.get(dto.getCategory()));
			expense.setTransactionDate(dto.getExpense_date().atStartOfDay());
			expense.setDescription(dto.getDescription());
			expense.setUser(user);
			expense.setType(TransactionType.EXPENSE);
			applyCurrencySnapshot(expense, globals.BASE_CURRENCY);
			return expense;
		}).toList();
		transactionRepository.saveAll(expenses);
		expenseFilesRepository.deleteById(fileUUID);
		return "Expenses inserted successfully";
	}

	public LinkedHashMap<String, Double> getMonthlyExpenseFromTillTo(String userId, int count,
	                                                                 globals.TimeFrame type) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		String displayCurrency = user.getCurrency();
		BigDecimal rate = resolveUsdRate(displayCurrency);

		DateRange range = resolveDateRange(count, type);

		LinkedHashMap<String, Double> baseTotals =
				transactionRepositoryCustomImpl.getMonthlyAmountFromTillTo(user.getId(), TransactionType.EXPENSE,
						range.startDate(),
						range.endDate());
		LinkedHashMap<String, Double> displayTotals = new LinkedHashMap<>();
		for (Map.Entry<String, Double> entry : baseTotals.entrySet()) {
			BigDecimal converted = BigDecimal.valueOf(entry.getValue())
					.multiply(rate)
					.setScale(2, java.math.RoundingMode.HALF_UP);
			displayTotals.put(entry.getKey(), converted.doubleValue());
		}
		return displayTotals;
	}

	public Map<String, Map<String, Double>> getMonthlyCategoryExpenseFromTillTo(String userId, int count,
	                                                                            globals.TimeFrame type) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		BigDecimal rate = resolveUsdRate(user.getCurrency());

		DateRange range = resolveDateRange(count, type);

		List<MonthlyCategoryExpense> dbRes =
				transactionRepositoryCustomImpl.getMonthlyCategoryExpenseFromTillTo(user.getId(),
						range.startDate(),
						range.endDate());

		Map<String, Map<String, Double>> monthlyCategoryExpense = new LinkedHashMap<>();

		List<Category> categories = categoryRepository.findByUserAndType(user, "expense");
		for (MonthlyCategoryExpense dto : dbRes) {
			String month = dto.getMonth().trim();
			monthlyCategoryExpense.computeIfAbsent(month, k -> {
				Map<String, Double> categoryMap = new LinkedHashMap<>();
				for (Category cat : categories) {
					categoryMap.put(cat.getName(), 0.0);
				}
				return categoryMap;
			});

			String category = dto.getCategoryName();
			Double amount = dto.getTotalAmount();
			BigDecimal converted = BigDecimal.valueOf(amount == null ? 0.0 : amount)
					.multiply(rate)
					.setScale(2, java.math.RoundingMode.HALF_UP);

			if (category != null) {
				monthlyCategoryExpense
						.computeIfAbsent(month, k -> new LinkedHashMap<>())
						.merge(category, converted.doubleValue(), Double::sum);

			}
		}

		return monthlyCategoryExpense;
	}

	public ExpenseOverview getExpensesOverviewByUserIdAndTimeFrame(
			String userId,
			LocalDateTime startDate,
			LocalDateTime endDate,
			int year,
			int month,
			LocalDateTime req_start_year,
			LocalDateTime req_end_year,
			LocalDateTime req_start,
			LocalDateTime req_end,
			Integer req_month) {

		UUID activeUserId = getActiveUserIdOrThrow(userId);

		User user = userService.GetActiveUserById(userId);
		String displayCurrency = user == null ? globals.BASE_CURRENCY : user.getCurrency();
		BigDecimal displayRate = resolveUsdRate(displayCurrency);

		CompletableFuture<List<ExpenseResponse>> f1 =
				CompletableFuture.supplyAsync(() ->
						mapExpensesToResponse(transactionRepository.findByUserIdAndTypeAndTimeFrameDesc(activeUserId, TransactionType.EXPENSE, startDate, endDate), displayCurrency), expenseExecutor);

		CompletableFuture<List<ExpenseResponse>> f2 =
				CompletableFuture.supplyAsync(() ->
						mapExpensesToResponse(transactionRepository.findByUserIdAndTypeAndTimeFrameDesc(activeUserId, TransactionType.EXPENSE, req_start_year, req_end_year), displayCurrency), expenseExecutor);

		CompletableFuture<List<ExpenseResponse>> f3 =
				CompletableFuture.supplyAsync(() ->
						mapExpensesToResponse(transactionRepository.findByUserIdAndTypeAndTimeFrameDesc(activeUserId, TransactionType.EXPENSE, req_start, req_end), displayCurrency), expenseExecutor);

		CompletableFuture<List<MonthlyCategoryExpense>> monthlyCategory =
				CompletableFuture.supplyAsync(() ->
						getMonthlyCategoryExpense(activeUserId, req_start_year, req_end_year), expenseExecutor);

		CompletableFuture<Iterable<Category>> categories =
				CompletableFuture.supplyAsync(() ->
						categoryService.getCategoriesByUserId(userId, globals.TYPE_EXPENSE), expenseExecutor);

		CompletableFuture<List<DailyExpense>> daily =
				CompletableFuture.supplyAsync(() ->
						getDailyExpense(activeUserId, req_start, req_end), expenseExecutor);

		CompletableFuture<ExpenseResList> recentExpenses =
				CompletableFuture.supplyAsync(() ->
						fetchExpensesWithConditions(userId,
								FormatDate.formatStartDate(null, false),
								endDate,
								"asc", null, 1, 1, "", null, null), expenseExecutor);

		CompletableFuture<List<Budget>> budgets =
				CompletableFuture.supplyAsync(() ->
						budgetService.getBudgetsByUserId(userId), expenseExecutor);

		CompletableFuture<Double> prevMonthTotal =
				CompletableFuture.supplyAsync(() ->
						getTotalExpenseForMonth(
								month == 1 ? year - 1 : year,
								month == 1 ? 12 : month - 1,
								activeUserId), expenseExecutor);

		CompletableFuture<List<RecurringExpenseDTO>> recurring =
				CompletableFuture.supplyAsync(() ->
						recurringExpenseService.findRecurringExpenseByUserId(userId), expenseExecutor);

		// join all
		CompletableFuture.allOf(
				f1, f2, f3, monthlyCategory, categories,
				daily, recentExpenses, budgets,
				prevMonthTotal, recurring
		).join();

		List<MonthlyCategoryExpense> convertedMonthly = new ArrayList<>();
		for (MonthlyCategoryExpense dto : monthlyCategory.join()) {
			convertedMonthly.add(new MonthlyCategoryExpense() {
				@Override
				public String getMonth() {
					return dto.getMonth();
				}

				@Override
				public String getCategoryName() {
					return dto.getCategoryName();
				}

				@Override
				public Double getTotalAmount() {
					BigDecimal amount = BigDecimal.valueOf(dto.getTotalAmount() == null ? 0.0 : dto.getTotalAmount());
					BigDecimal converted = amount.multiply(displayRate).setScale(2, java.math.RoundingMode.HALF_UP);
					return converted.doubleValue();
				}
			});
		}

		List<DailyExpense> convertedDaily = new ArrayList<>();
		for (DailyExpense dto : daily.join()) {
			convertedDaily.add(new DailyExpense() {
				@Override
				public String getExpenseDate() {
					return dto.getExpenseDate();
				}

				@Override
				public Double getTotalAmount() {
					BigDecimal amount = BigDecimal.valueOf(dto.getTotalAmount() == null ? 0.0 : dto.getTotalAmount());
					BigDecimal converted = amount.multiply(displayRate).setScale(2, java.math.RoundingMode.HALF_UP);
					return converted.doubleValue();
				}
			});
		}

		Double prevMonthDisplay = BigDecimal.valueOf(prevMonthTotal.join() == null ? 0.0 : prevMonthTotal.join())
				.multiply(displayRate)
				.setScale(2, java.math.RoundingMode.HALF_UP)
				.doubleValue();

		return new ExpenseOverview(
				f1.join(),
				f2.join(),
				f3.join(),
				userId,
				convertedMonthly,
				categories.join(),
				convertedDaily,
				recentExpenses.join(),
				req_month,
				budgets.join(),
				prevMonthDisplay,
				recurring.join()
		);
	}

	public Map<String, String> GenerateS3PresignedURLForExpense(String userId,
	                                                            String fileName,
	                                                            String ContentType) {

		String key = userId + "/" + fileName;
		try {
			return Map.of("presignedURL", s3Service.generatePresignedURL(key,
					ContentType), "fileURL", key);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public void UpdateExpenseAttachment(String UserID, String expenseId,
	                                    String key) {

		try {
			Transaction expense =
					transactionRepository.findByUserIdAndIdAndType(UUID.fromString(UserID),
							UUID.fromString(expenseId), TransactionType.EXPENSE);

			if (expense == null) {
				throw new IllegalArgumentException("Expense not found");
			}

			expense.setReceiptUrl(key);

			transactionRepository.save(expense);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}


	}

	public String GenerateDownloadURLForExpense(String eid) {
		try {
			Optional<Transaction> expense =
					transactionRepository.findById(UUID.fromString(eid));
			if (expense.isEmpty()) {
				throw new IllegalArgumentException("Expense not found");
			}
			if (expense.get().getReceiptUrl() == null) {
				throw new IllegalArgumentException("Receipt URL not found");
			}
			return s3Service.generateDownloadUrl(expense.get().getReceiptUrl());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void DeleteExpenseAttachment(String userId, String expenseId) {
		try {
			Transaction expense = transactionRepository.findByUserIdAndIdAndType(UUID.fromString(userId),
					UUID.fromString(expenseId), TransactionType.EXPENSE);
			if (expense == null) {
				throw new IllegalArgumentException("Expense not found");
			}
			String receiptUrl = expense.getReceiptUrl();
			if (receiptUrl == null || receiptUrl.isBlank()) {
				throw new IllegalArgumentException("Receipt URL not found");
			}
			s3Service.deleteObject(receiptUrl);
			expense.setReceiptUrl(null);
			transactionRepository.save(expense);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//	dont remove
	private record DateRange(LocalDateTime startDate, LocalDateTime endDate) {
	}


}
