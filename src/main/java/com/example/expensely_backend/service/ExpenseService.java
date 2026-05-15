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

	private final ExpenseRepository expenseRepository;
	private final CategoryService categoryService;
	private final UserService userService;
	private final BudgetService budgetService;
	private final ExpenseRepositoryCustomImpl expenseRepositoryCustomImpl;
	private final ExpenseFilesRepository expenseFilesRepository;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;
	private final CategoryRepository categoryRepository;
	private final DbLogService dbLogService;
	private final RecurringExpenseService recurringExpenseService;
	private final Executor expenseExecutor;

	@Autowired
	private S3Service s3Service;

	public ExpenseService(ExpenseRepository expenseRepository, CategoryService categoryService,
	                      UserService userService,
	                      BudgetService budgetService, ExpenseRepositoryCustomImpl expenseRepositoryCustomImpl
			, ExpenseFilesRepository expenseFilesService, UserRepository userRepository,
			              ObjectMapper objectMapper,
			              CategoryRepository categoryRepository,
			              DbLogService dbLogService,
			              RecurringExpenseService recurringExpenseService,
			              @Qualifier("expenseExecutor") Executor expenseExecutor) {
		this.expenseRepository = expenseRepository;
		this.categoryService = categoryService;
		this.userService = userService;
		this.budgetService = budgetService;
		this.expenseRepositoryCustomImpl = expenseRepositoryCustomImpl;
		this.expenseFilesRepository = expenseFilesService;
		this.userRepository = userRepository;
		this.objectMapper = objectMapper;
		this.categoryRepository = categoryRepository;
		this.dbLogService = dbLogService;
		this.recurringExpenseService = recurringExpenseService;
		this.expenseExecutor = expenseExecutor;

	}

	private UUID getActiveUserIdOrThrow(String userId) {
		return userService.GetActiveUserById(userId).getId();
	}

	private List<ExpenseResponse> getExpenseByUserIdAndStartDateAndEndDate(UUID userId, LocalDateTime startDate, LocalDateTime endDate, String order) {
		List<Expense> expenses;
		if (order == null || order.equalsIgnoreCase("desc")) {
			expenses = expenseRepository.findByUserIdAndTimeFrameDesc(userId, startDate, endDate);
		} else if (order.equalsIgnoreCase("asc")) {
			expenses = expenseRepository.findByUserIdAndTimeFrameAsc(userId, startDate, endDate);

		} else {
			throw new IllegalArgumentException("Order must be 'asc' or 'desc'");
		}

		return expenses.stream().map(ExpenseResponse::new).collect(Collectors.toList());
	}

	private List<MonthlyCategoryExpense> getMonthlyCategoryExpense(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
		return expenseRepository.findMonthlyCategoryExpenseByUserId(userId, startDate, endDate);
	}

	private List<DailyExpense> getDailyExpense(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
		return expenseRepository.findDailyExpenseByUserIdAndTimeFrame(userId, startDate, endDate);
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

		List<Expense> expenses = expenseRepositoryCustomImpl.findExpenses(userId, startDate, endDate,
				categoryUUID, q, offset, limit, customSortBy, customSortOrder, order);
		long totalElements = expenseRepositoryCustomImpl.countExpenses(userId, startDate, endDate,
				categoryUUID, q);

		totalPages = (int) Math.ceil((double) totalElements / limit);
		return new ExpenseResList(expenses.stream().map(ExpenseResponse::new).collect(Collectors.toList()), totalPages, totalElements, page);
	}

	private Double getTotalExpenseForMonth(int year, int month, UUID userId) {
		LocalDateTime startDate = FormatDate.formatStartDate(LocalDateTime.of(year, month, 1, 0, 0), false);
		YearMonth reqYm = YearMonth.of(year, month);
		LocalDateTime endDate = reqYm.atEndOfMonth().atTime(23, 59, 59);

		return expenseRepository.getTotalExpenseByUserId(userId, startDate, endDate);
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

	public Expense save(Expense expense) {
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
		Expense exp = expenseRepository.save(expense);


		// calculate if budget set

		try {
			budgetService.updateBudgetAmountByUserIdAndCategoryId(user.getId().toString(), category.getId().toString(), expense.getAmount(), expense.getExpenseDate());
		} catch (Exception e) {
			throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
		}

		return exp;

	}

	public Expense getExpenseById(String id) {
		return expenseRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("Expense not found"));
	}

	public void deleteExpenseById(String id) {
		try {
			if (!expenseRepository.existsById(UUID.fromString(id))) {
				throw new IllegalArgumentException("Expense not found");
			}
			//budget update
			Expense expense = getExpenseById(id);

			expenseRepository.deleteById(UUID.fromString(id));

			try {
				budgetService.updateBudgetAmountByUserIdAndCategoryId(expense.getUser().getId().toString(), expense.getCategory().getId().toString(), expense.getAmount().negate(), expense.getExpenseDate());
			} catch (Exception e) {
				throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Error deleting expense: " + e.getMessage());
		}
	}

	public Iterable<Expense> getExpensesByUserId(String userId) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		return expenseRepository.findByUserId(user.getId());
	}

	public List<Expense> getExpensesByCategoryIdAndUserID(String categoryId, String userId) {
		Category category = categoryService.getCategoryById(categoryId);
		if (category == null) {
			throw new IllegalArgumentException("Category not found");
		}
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}

		return expenseRepository.findByCategoryIdAndUserId(category.getId(), user.getId());
	}

	public Expense updateExpense(Expense expense) {

		Expense oldExpense = getExpenseById(expense.getId().toString());
		BigDecimal changed_amount = expense.getAmount().subtract(oldExpense.getAmount());

		if (expense.getCategory() != null && expense.getCategory().getId() != null && !expense.getCategory().getId().equals(oldExpense.getCategory().getId())) {
			Category category = categoryService.getCategoryById(expense.getCategory().getId().toString());
			if (category == null) {
				throw new IllegalArgumentException("Category not found");
			}
			oldExpense.setCategory(category);
		}


		if (!expenseRepository.existsById(expense.getId())) {
			throw new IllegalArgumentException("Expense not found");
		}


		if (expense.getAmount() != null && expense.getAmount().compareTo(oldExpense.getAmount()) != 0) {
			oldExpense.setAmount(expense.getAmount());
		}
		if (expense.getDescription() != null && !expense.getDescription().equals(oldExpense.getDescription())) {
			oldExpense.setDescription(expense.getDescription());
		}
		if (expense.getExpenseDate() != null && !expense.getExpenseDate().equals(oldExpense.getExpenseDate())) {
			oldExpense.setExpenseDate(expense.getExpenseDate());
		}


		Expense exp = expenseRepository.save(oldExpense);

//        update budget
		try {
			budgetService.updateBudgetAmountByUserIdAndCategoryId(exp.getUser().getId().toString(), exp.getCategory().getId().toString(), changed_amount, exp.getExpenseDate());
		} catch (Exception e) {
			throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
		}
		return exp;
	}

	public List<ExpenseResponse> getExpenseByUserIdAndStartDateAndEndDate(String userId, LocalDateTime startDate, LocalDateTime endDate, String order) {
		return getExpenseByUserIdAndStartDateAndEndDate(getActiveUserIdOrThrow(userId), startDate, endDate, order);
	}

	public void deleteBuUserIDAndExpenseIds(String userId, List<Expense> expenses) {
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

		for (Expense expense : expenses) {
			if (!expense.getUser().getId().equals(user.getId())) {
				throw new IllegalArgumentException("Expense does not belong to user");
			}
		}
		expenseRepository.deleteAll(expenses);
		for (Expense expense : expenses) {
			try {
				budgetService.updateBudgetAmountByUserIdAndCategoryId(expense.getUser().getId().toString(), expense.getCategory().getId().toString(), expense.getAmount().negate(), expense.getExpenseDate());
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

	public List<MonthlyCategoryExpense> getMonthlyCategoryExpense(String userId, LocalDateTime startDate, LocalDateTime endDate) {
		return getMonthlyCategoryExpense(getActiveUserIdOrThrow(userId), startDate, endDate);

	}

	public List<DailyExpense> getDailyExpense(String userId, LocalDateTime startDate, LocalDateTime endDate) {
		return getDailyExpense(getActiveUserIdOrThrow(userId), startDate, endDate);
	}

	public String exportExpensesToCSV(String userId, LocalDateTime startDate, LocalDateTime endDate) throws IOException {
		UUID userIdUUID = UUID.fromString(userId);
		User user = userService.GetActiveUserById(userId);
		List<Expense> expenses = expenseRepository.findByUserIdAndTimeFrameAsc(userIdUUID, startDate, endDate);

		StringWriter sw = new StringWriter();
		CSVWriter writer = new CSVWriter(sw);

		// header
		writer.writeNext(new String[]{"Date", "Description", "Amount (in " + user.getCurrency() + ")", "Category"});

		// rows
		for (Expense expense : expenses) {
			writer.writeNext(new String[]{
					expense.getExpenseDate().toString(),
					expense.getDescription(),
					String.valueOf(expense.getAmount()),
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
		List<Expense> expenses = rows.stream().map(dto -> {
			Expense expense = new Expense();
			expense.setAmount(BigDecimal.valueOf(dto.getAmount()));
			expense.setCategory(catsList.get(dto.getCategory()));
			expense.setExpenseDate(dto.getExpense_date().atStartOfDay());
			expense.setDescription(dto.getDescription());
			expense.setUser(user);
			return expense;
		}).toList();
		expenseRepository.saveAll(expenses);
		expenseFilesRepository.deleteById(fileUUID);
		return "Expenses inserted successfully";
	}

	public Double getTotalExpenseForMonth(int year, int month, String userId) {
		return getTotalExpenseForMonth(year, month, UUID.fromString(userId));

	}

	public LinkedHashMap<String, Double> getMonthlyExpenseFromTillTo(String userId, int count,
	                                                                 globals.TimeFrame type) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}

		DateRange range = resolveDateRange(count, type);

		return
				expenseRepositoryCustomImpl.getMonthlyExpenseFromTillTo(user.getId(),
						range.startDate(),
						range.endDate());
	}

	public Map<String, Map<String, Double>> getMonthlyCategoryExpenseFromTillTo(String userId, int count,
	                                                                            globals.TimeFrame type) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}

		DateRange range = resolveDateRange(count, type);

		List<MonthlyCategoryExpense> dbRes =
				expenseRepositoryCustomImpl.getMonthlyCategoryExpenseFromTillTo(user.getId(),
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

			if (category != null) {
				monthlyCategoryExpense
						.computeIfAbsent(month, k -> new LinkedHashMap<>())
						.merge(category, amount, Double::sum);

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

		CompletableFuture<List<ExpenseResponse>> f1 =
				CompletableFuture.supplyAsync(() ->
						getExpenseByUserIdAndStartDateAndEndDate(activeUserId, startDate, endDate, "desc"), expenseExecutor);

		CompletableFuture<List<ExpenseResponse>> f2 =
				CompletableFuture.supplyAsync(() ->
						getExpenseByUserIdAndStartDateAndEndDate(activeUserId, req_start_year, req_end_year, "desc"), expenseExecutor);

		CompletableFuture<List<ExpenseResponse>> f3 =
				CompletableFuture.supplyAsync(() ->
						getExpenseByUserIdAndStartDateAndEndDate(activeUserId, req_start, req_end, "desc"), expenseExecutor);

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
						fetchExpensesWithConditions(activeUserId,
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

		return new ExpenseOverview(
				f1.join(),
				f2.join(),
				f3.join(),
				userId,
				monthlyCategory.join(),
				categories.join(),
				daily.join(),
				recentExpenses.join(),
				req_month,
				budgets.join(),
				prevMonthTotal.join(),
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
			Expense expense =
					expenseRepository.findByUserIdAndId(UUID.fromString(UserID),
							UUID.fromString(expenseId));

			if (expense == null) {
				throw new IllegalArgumentException("Expense not found");
			}

			expense.setReceiptUrl(key);

			expenseRepository.save(expense);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}


	}

	public String GenerateDownloadURLForExpense(String eid) {
		try {
			Optional<Expense> expense =
					expenseRepository.findById(UUID.fromString(eid));
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
			Expense expense = expenseRepository.findByUserIdAndId(UUID.fromString(userId),
					UUID.fromString(expenseId));
			if (expense == null) {
				throw new IllegalArgumentException("Expense not found");
			}
			String receiptUrl = expense.getReceiptUrl();
			if (receiptUrl == null || receiptUrl.isBlank()) {
				throw new IllegalArgumentException("Receipt URL not found");
			}
			s3Service.deleteObject(receiptUrl);
			expense.setReceiptUrl(null);
			expenseRepository.save(expense);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private record DateRange(LocalDateTime startDate, LocalDateTime endDate) {
	}


}
