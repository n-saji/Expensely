package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.DailyIncome;
import com.example.expensely_backend.dto.IncomeOverview;
import com.example.expensely_backend.dto.IncomeResList;
import com.example.expensely_backend.dto.IncomeResponse;
import com.example.expensely_backend.dto.MonthlyCategoryIncome;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Income;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.CategoryRepository;
import com.example.expensely_backend.repository.ExpenseRepository;
import com.example.expensely_backend.repository.IncomeRepository;
import com.example.expensely_backend.repository.IncomeRepositoryCustomImpl;
import com.example.expensely_backend.utils.FormatDate;
import com.opencsv.CSVWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class IncomeService {

	private static final String BASE_CURRENCY = "USD";

	private final IncomeRepository incomeRepository;
	private final CategoryService categoryService;
	private final UserService userService;
	private final IncomeRepositoryCustomImpl incomeRepositoryCustomImpl;
	private final CategoryRepository categoryRepository;
	private final ExpenseRepository expenseRepository;
	private final Executor expenseExecutor;
	private final ExchangeRateService exchangeRateService;

	public IncomeService(IncomeRepository incomeRepository,
	                     CategoryService categoryService,
	                     UserService userService,
	                     IncomeRepositoryCustomImpl incomeRepositoryCustomImpl,
	                     CategoryRepository categoryRepository,
	                     ExpenseRepository expenseRepository,
	                     @Qualifier("expenseExecutor") Executor expenseExecutor,
	                     ExchangeRateService exchangeRateService) {
		this.incomeRepository = incomeRepository;
		this.categoryService = categoryService;
		this.userService = userService;
		this.incomeRepositoryCustomImpl = incomeRepositoryCustomImpl;
		this.categoryRepository = categoryRepository;
		this.expenseRepository = expenseRepository;
		this.expenseExecutor = expenseExecutor;
		this.exchangeRateService = exchangeRateService;
	}

	private BigDecimal normalizeAmount(BigDecimal amount) {
		return exchangeRateService.normalizeDisplayAmount(amount);
	}

	private BigDecimal resolveUsdRate(String currency) {
		return exchangeRateService.getUsdToCurrencyRate(currency);
	}

	private void applyCurrencySnapshot(Income income, String currency) {
		income.setAmount(normalizeAmount(income.getAmount()));
		income.setCurrency(currency.toUpperCase());
		income.setBaseCurrency(BASE_CURRENCY);
		BigDecimal rate = resolveUsdRate(currency);
		income.setExchangeRate(rate);
		income.setBaseCurrencyAmount(exchangeRateService.convertToUsd(income.getAmount(), currency));
	}

	private List<IncomeResponse> mapIncomesToResponse(List<Income> incomes, String displayCurrency) {
		BigDecimal displayRate = resolveUsdRate(displayCurrency);
		return incomes.stream()
				.map(income -> {
					BigDecimal displayAmount = income.getBaseCurrencyAmount() == null
							? normalizeAmount(income.getAmount())
							: income.getBaseCurrencyAmount().multiply(displayRate).setScale(2, RoundingMode.HALF_UP);
					return new IncomeResponse(income, displayCurrency, displayAmount);
				})
				.collect(Collectors.toList());
	}

	private IncomeResponse mapIncomeToResponse(Income income, String displayCurrency) {
		BigDecimal displayRate = resolveUsdRate(displayCurrency);
		BigDecimal displayAmount = income.getBaseCurrencyAmount() == null
				? normalizeAmount(income.getAmount())
				: income.getBaseCurrencyAmount().multiply(displayRate).setScale(2, RoundingMode.HALF_UP);
		return new IncomeResponse(income, displayCurrency, displayAmount);
	}

	private UUID getActiveUserIdOrThrow(String userId) {
		return userService.GetActiveUserById(userId).getId();
	}

	private List<IncomeResponse> getIncomeByUserIdAndStartDateAndEndDate(UUID userId, LocalDateTime startDate, LocalDateTime endDate, String order) {
		List<Income> incomes;
		if (order == null || order.equalsIgnoreCase("desc")) {
			incomes = incomeRepository.findByUserIdAndTimeFrameDesc(userId, startDate, endDate);
		} else if (order.equalsIgnoreCase("asc")) {
			incomes = incomeRepository.findByUserIdAndTimeFrameAsc(userId, startDate, endDate);
		} else {
			throw new IllegalArgumentException("Order must be 'asc' or 'desc'");
		}

		User user = userService.GetActiveUserById(userId.toString());
		String displayCurrency = user == null ? BASE_CURRENCY : user.getCurrency();
		return mapIncomesToResponse(incomes, displayCurrency);
	}

	private List<MonthlyCategoryIncome> getMonthlyCategoryIncome(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
		return incomeRepository.findMonthlyCategoryIncomeByUserId(userId, startDate, endDate);
	}

	private List<DailyIncome> getDailyIncome(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
		return incomeRepository.findDailyIncomeByUserIdAndTimeFrame(userId, startDate, endDate);
	}

	private record DateRange(LocalDateTime startDate, LocalDateTime endDate) {
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
			case YEAR -> startDate = LocalDateTime.of(date.getYear() - count, date.getMonth(), 1, 0, 0, 0);
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
			default -> throw new IllegalArgumentException("Invalid time frame type");
		}

		YearMonth dateYm = YearMonth.of(date.getYear(), date.getMonth());
		LocalDateTime endDate = LocalDateTime.of(date.getYear(), date.getMonth(), dateYm.lengthOfMonth(), 23, 59, 59);

		return new DateRange(startDate, endDate);
	}

	public IncomeOverview getIncomeOverviewByUserIdAndTimeFrame(
			String userId,
			LocalDateTime startDate,
			LocalDateTime endDate,
			int year,
			int month,
			LocalDateTime reqStartYear,
			LocalDateTime reqEndYear,
			LocalDateTime reqStart,
			LocalDateTime reqEnd,
			Integer reqMonth) {

		UUID activeUserId = getActiveUserIdOrThrow(userId);
		User user = userService.GetActiveUserById(userId);
		String displayCurrency = user == null ? BASE_CURRENCY : user.getCurrency();
		BigDecimal displayRate = resolveUsdRate(displayCurrency);

		CompletableFuture<List<IncomeResponse>> f1 =
				CompletableFuture.supplyAsync(() ->
						getIncomeByUserIdAndStartDateAndEndDate(activeUserId, startDate, endDate, "desc"), expenseExecutor);

		CompletableFuture<List<IncomeResponse>> f2 =
				CompletableFuture.supplyAsync(() ->
						getIncomeByUserIdAndStartDateAndEndDate(activeUserId, reqStartYear, reqEndYear, "desc"), expenseExecutor);

		CompletableFuture<List<IncomeResponse>> f3 =
				CompletableFuture.supplyAsync(() ->
						getIncomeByUserIdAndStartDateAndEndDate(activeUserId, reqStart, reqEnd, "desc"), expenseExecutor);

		CompletableFuture<List<MonthlyCategoryIncome>> monthlyCategory =
				CompletableFuture.supplyAsync(() ->
						getMonthlyCategoryIncome(activeUserId, reqStartYear, reqEndYear), expenseExecutor);

		CompletableFuture<Iterable<Category>> categories =
				CompletableFuture.supplyAsync(() ->
						categoryService.getCategoriesByUserId(userId, globals.TYPE_INCOME), expenseExecutor);

		CompletableFuture<List<DailyIncome>> daily =
				CompletableFuture.supplyAsync(() ->
						getDailyIncome(activeUserId, reqStart, reqEnd), expenseExecutor);

		CompletableFuture<Income> firstIncome =
				CompletableFuture.supplyAsync(() ->
						incomeRepository.findFirstByUserIdOrderByIncomeDateAsc(activeUserId), expenseExecutor);

		CompletableFuture<Double> prevMonthTotal =
				CompletableFuture.supplyAsync(() ->
						getTotalIncomeForMonth(
								month == 1 ? year - 1 : year,
								month == 1 ? 12 : month - 1,
								userId), expenseExecutor);

		CompletableFuture<Double> totalIncomeAllTime =
				CompletableFuture.supplyAsync(() -> {
					Double total = incomeRepository.getTotalIncomeByUserId(
							activeUserId,
							LocalDateTime.of(1970, 1, 1, 0, 0),
							LocalDateTime.now());
					return total == null ? 0.0 : total;
				}, expenseExecutor);

		CompletableFuture<Double> totalExpenseAllTime =
				CompletableFuture.supplyAsync(() -> {
					Double total = expenseRepository.getTotalExpenseByUserId(
							activeUserId,
							LocalDateTime.of(1970, 1, 1, 0, 0),
							LocalDateTime.now());
					return total == null ? 0.0 : total;
				}, expenseExecutor);

		CompletableFuture.allOf(
				f1, f2, f3, monthlyCategory,
				categories, daily, firstIncome,
				prevMonthTotal, totalIncomeAllTime,
				totalExpenseAllTime
		).join();

		List<MonthlyCategoryIncome> convertedMonthly = new ArrayList<>();
		for (MonthlyCategoryIncome dto : monthlyCategory.join()) {
			convertedMonthly.add(new MonthlyCategoryIncome() {
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
					BigDecimal converted = amount.multiply(displayRate).setScale(2, RoundingMode.HALF_UP);
					return converted.doubleValue();
				}
			});
		}

		List<DailyIncome> convertedDaily = new ArrayList<>();
		for (DailyIncome dto : daily.join()) {
			convertedDaily.add(new DailyIncome() {
				@Override
				public String getIncomeDate() {
					return dto.getIncomeDate();
				}

				@Override
				public Double getTotalAmount() {
					BigDecimal amount = BigDecimal.valueOf(dto.getTotalAmount() == null ? 0.0 : dto.getTotalAmount());
					BigDecimal converted = amount.multiply(displayRate).setScale(2, RoundingMode.HALF_UP);
					return converted.doubleValue();
				}
			});
		}

		Double prevMonthDisplay = BigDecimal.valueOf(prevMonthTotal.join() == null ? 0.0 : prevMonthTotal.join())
				.multiply(displayRate)
				.setScale(2, RoundingMode.HALF_UP)
				.doubleValue();

		Double totalIncomeDisplay = BigDecimal.valueOf(totalIncomeAllTime.join() == null ? 0.0 : totalIncomeAllTime.join())
				.multiply(displayRate)
				.setScale(2, RoundingMode.HALF_UP)
				.doubleValue();
		Double totalExpenseDisplay = BigDecimal.valueOf(totalExpenseAllTime.join() == null ? 0.0 : totalExpenseAllTime.join())
				.multiply(displayRate)
				.setScale(2, RoundingMode.HALF_UP)
				.doubleValue();

		return new IncomeOverview(
				f1.join(),
				f2.join(),
				f3.join(),
				userId,
				convertedMonthly,
				categories.join(),
				convertedDaily,
				reqMonth,
				firstIncome.join(),
				prevMonthDisplay,
				totalIncomeDisplay - totalExpenseDisplay);
	}

	public Income save(Income income) {
		if (income.getCategory() == null || income.getCategory().getId() == null) {
			throw new IllegalArgumentException("Category must be provided");
		}
		Category category = categoryService.getCategoryById(income.getCategory().getId().toString());
		if (!globals.TYPE_INCOME.equals(category.getType())) {
			throw new IllegalArgumentException("Category must be income type");
		}
		income.setCategory(category);

		if (income.getUser() == null || income.getUser().getId() == null) {
			throw new IllegalArgumentException("User must be provided");
		}
		User user = userService.GetActiveUserById(income.getUser().getId().toString());
		income.setUser(user);

		if (income.getIncomeDate() == null) {
			income.setIncomeDate(LocalDateTime.now());
		}

		String currency = income.getCurrency();
		if (currency == null || currency.isBlank()) {
			currency = user.getCurrency();
		}
		applyCurrencySnapshot(income, currency);

		return incomeRepository.save(income);
	}

	public IncomeResponse getIncomeResponseById(String id) {
		Income income = getIncomeById(id);
		String displayCurrency = income.getUser() == null ? BASE_CURRENCY : income.getUser().getCurrency();
		return mapIncomeToResponse(income, displayCurrency);
	}

	public Income getIncomeById(String id) {
		return incomeRepository.findById(UUID.fromString(id))
				.orElseThrow(() -> new IllegalArgumentException("Income not found"));
	}

	public Income getIncomeByIdForUser(String id, String userId) {
		Income income = getIncomeById(id);
		User user = userService.GetActiveUserById(userId);
		if (!income.getUser().getId().equals(user.getId())) {
			throw new IllegalArgumentException("Income does not belong to user");
		}
		return income;
	}

	public void deleteIncomeById(String id) {
		try {
			if (!incomeRepository.existsById(UUID.fromString(id))) {
				throw new IllegalArgumentException("Income not found");
			}
			incomeRepository.deleteById(UUID.fromString(id));
		} catch (Exception e) {
			throw new IllegalArgumentException("Error deleting income: " + e.getMessage());
		}
	}

	public void deleteIncomeByIdForUser(String id, String userId) {
		Income income = getIncomeByIdForUser(id, userId);
		try {
			incomeRepository.deleteById(income.getId());
		} catch (Exception e) {
			throw new IllegalArgumentException("Error deleting income: " + e.getMessage());
		}
	}

	public void deleteByUserIDAndIncomeIds(String userId, List<Income> incomes) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}

		for (int i = 0; i < incomes.size(); i++) {
			if (incomes.get(i).getId() == null) {
				throw new IllegalArgumentException("Income ID must be provided");
			}
			incomes.set(i, getIncomeById(incomes.get(i).getId().toString()));
		}

		for (Income income : incomes) {
			if (!income.getUser().getId().equals(user.getId())) {
				throw new IllegalArgumentException("Income does not belong to user");
			}
		}

		incomeRepository.deleteAll(incomes);
	}

	public List<IncomeResponse> getIncomesByUserId(String userId) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		return mapIncomesToResponse(incomeRepository.findByUserId(user.getId()), user.getCurrency());
	}

	public List<IncomeResponse> getIncomesByCategoryIdAndUserID(String categoryId, String userId) {
		Category category = categoryService.getCategoryById(categoryId);
		if (!globals.TYPE_INCOME.equals(category.getType())) {
			throw new IllegalArgumentException("Category must be income type");
		}
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		return mapIncomesToResponse(incomeRepository.findByCategoryIdAndUserId(category.getId(), user.getId()), user.getCurrency());
	}

	public Income updateIncome(Income income) {
		if (income.getId() == null) {
			throw new IllegalArgumentException("Income ID must be provided");
		}
		Income oldIncome = getIncomeById(income.getId().toString());

		if (income.getCategory() != null && income.getCategory().getId() != null
				&& !income.getCategory().getId().equals(oldIncome.getCategory().getId())) {
			Category category = categoryService.getCategoryById(income.getCategory().getId().toString());
			if (!globals.TYPE_INCOME.equals(category.getType())) {
				throw new IllegalArgumentException("Category must be income type");
			}
			oldIncome.setCategory(category);
		}

		if (income.getAmount() != null && income.getAmount().compareTo(oldIncome.getAmount()) != 0) {
			oldIncome.setAmount(normalizeAmount(income.getAmount()));
			String currency = oldIncome.getCurrency() == null ? BASE_CURRENCY : oldIncome.getCurrency();
			BigDecimal rate = oldIncome.getExchangeRate();
			if (rate == null) {
				rate = resolveUsdRate(currency);
			}
			if (BASE_CURRENCY.equalsIgnoreCase(currency)) {
				oldIncome.setBaseCurrencyAmount(oldIncome.getAmount().setScale(2, RoundingMode.HALF_UP));
			} else {
				oldIncome.setBaseCurrencyAmount(oldIncome.getAmount()
						.divide(rate, 8, RoundingMode.HALF_UP)
						.setScale(2, RoundingMode.HALF_UP));
			}
		}
		if (income.getDescription() != null && !income.getDescription().equals(oldIncome.getDescription())) {
			oldIncome.setDescription(income.getDescription());
		}
		if (income.getIncomeDate() != null && !income.getIncomeDate().equals(oldIncome.getIncomeDate())) {
			oldIncome.setIncomeDate(income.getIncomeDate());
		}

		return incomeRepository.save(oldIncome);
	}

	public List<IncomeResponse> getIncomeByUserIdAndStartDateAndEndDate(String userId, LocalDateTime startDate, LocalDateTime endDate, String order) {
		return getIncomeByUserIdAndStartDateAndEndDate(getActiveUserIdOrThrow(userId), startDate, endDate, order);
	}

	public List<MonthlyCategoryIncome> getMonthlyCategoryIncome(String userId, LocalDateTime startDate, LocalDateTime endDate) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		BigDecimal rate = resolveUsdRate(user.getCurrency());
		List<MonthlyCategoryIncome> converted = new ArrayList<>();
		for (MonthlyCategoryIncome dto : getMonthlyCategoryIncome(getActiveUserIdOrThrow(userId), startDate, endDate)) {
			converted.add(new MonthlyCategoryIncome() {
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
					BigDecimal converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
					return converted.doubleValue();
				}
			});
		}
		return converted;
	}

	public List<DailyIncome> getDailyIncome(String userId, LocalDateTime startDate, LocalDateTime endDate) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		BigDecimal rate = resolveUsdRate(user.getCurrency());
		List<DailyIncome> converted = new ArrayList<>();
		for (DailyIncome dto : getDailyIncome(getActiveUserIdOrThrow(userId), startDate, endDate)) {
			converted.add(new DailyIncome() {
				@Override
				public String getIncomeDate() {
					return dto.getIncomeDate();
				}

				@Override
				public Double getTotalAmount() {
					BigDecimal amount = BigDecimal.valueOf(dto.getTotalAmount() == null ? 0.0 : dto.getTotalAmount());
					BigDecimal converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
					return converted.doubleValue();
				}
			});
		}
		return converted;
	}

	public LinkedHashMap<String, Double> getMonthlyIncomeFromTillTo(String userId, int count,
	                                                                globals.TimeFrame type) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		String displayCurrency = user.getCurrency();
		BigDecimal rate = resolveUsdRate(displayCurrency);

		DateRange range = resolveDateRange(count, type);

		LinkedHashMap<String, Double> baseTotals =
				incomeRepositoryCustomImpl.getMonthlyIncomeFromTillTo(user.getId(), range.startDate(), range.endDate());
		LinkedHashMap<String, Double> displayTotals = new LinkedHashMap<>();
		for (Map.Entry<String, Double> entry : baseTotals.entrySet()) {
			BigDecimal converted = BigDecimal.valueOf(entry.getValue())
					.multiply(rate)
					.setScale(2, RoundingMode.HALF_UP);
			displayTotals.put(entry.getKey(), converted.doubleValue());
		}
		return displayTotals;
	}

	public LinkedHashMap<String, java.util.Map<String, Double>> getMonthlyCategoryIncomeFromTillTo(String userId, int count,
	                                                                                               globals.TimeFrame type) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		BigDecimal rate = resolveUsdRate(user.getCurrency());

		DateRange range = resolveDateRange(count, type);

		List<MonthlyCategoryIncome> dbRes =
				incomeRepositoryCustomImpl.getMonthlyCategoryIncomeFromTillTo(user.getId(), range.startDate(), range.endDate());

		java.util.Map<String, java.util.Map<String, Double>> monthlyCategoryIncome = new LinkedHashMap<>();

		List<Category> categories = categoryRepository.findByUserAndType(user, globals.TYPE_INCOME);
		for (MonthlyCategoryIncome dto : dbRes) {
			String month = dto.getMonth().trim();
			monthlyCategoryIncome.computeIfAbsent(month, k -> {
				java.util.Map<String, Double> categoryMap = new LinkedHashMap<>();
				for (Category cat : categories) {
					categoryMap.put(cat.getName(), 0.0);
				}
				return categoryMap;
			});

			String category = dto.getCategoryName();
			Double amount = dto.getTotalAmount();
			BigDecimal converted = BigDecimal.valueOf(amount == null ? 0.0 : amount)
					.multiply(rate)
					.setScale(2, RoundingMode.HALF_UP);

			if (category != null) {
				monthlyCategoryIncome
						.computeIfAbsent(month, k -> new LinkedHashMap<>())
						.merge(category, converted.doubleValue(), Double::sum);
			}
		}

		return new LinkedHashMap<>(monthlyCategoryIncome);
	}

	public Double getTotalIncomeForMonth(int year, int month, String userId) {
		UUID userIdUUID = UUID.fromString(userId);
		LocalDateTime startDate = FormatDate.formatStartDate(LocalDateTime.of(year, month, 1, 0, 0), false);
		YearMonth req_ym = YearMonth.of(year, month);
		LocalDateTime endDate = req_ym.atEndOfMonth().atTime(23, 59, 59);
		Double total = incomeRepository.getTotalIncomeByUserId(userIdUUID, startDate, endDate);
		return total == null ? 0.0 : total;
	}

	public Double getTotalIncomeForYear(int year, String userId) {
		UUID userIdUUID = UUID.fromString(userId);
		LocalDateTime startDate = LocalDateTime.of(year, 1, 1, 0, 0);
		LocalDateTime endDate = LocalDateTime.of(year, 12, 31, 23, 59, 59);
		Double total = incomeRepository.getTotalIncomeByUserId(userIdUUID, startDate, endDate);
		return total == null ? 0.0 : total;
	}

	public Income getFirstIncome(String userId) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		return incomeRepository.findFirstByUserIdOrderByIncomeDateAsc(user.getId());
	}

	public IncomeResList fetchIncomesWithConditions(String userId, LocalDateTime startDate,
	                                                LocalDateTime endDate, String order,
	                                                String categoryId, int page, int limit,
	                                                String q, String customSortBy, String customSortOrder) {
		long totalPages, totalElements = 0;
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		if (page < 1) {
			throw new IllegalArgumentException("Page must be greater than 0");
		}
		int offset = (page - 1) * limit;

		List<Income> incomes;
		UUID categoryUUID = null;
		if (q == null) q = "";
		if (order == null) order = "desc";
		else order = order.toLowerCase();

		if (categoryId != null) {
			Category category = categoryService.getCategoryById(categoryId);
			if (!globals.TYPE_INCOME.equals(category.getType())) {
				throw new IllegalArgumentException("Category must be income type");
			}
			categoryUUID = category.getId();
		}

		incomes = incomeRepositoryCustomImpl.findIncomes(user.getId(), startDate, endDate,
				categoryUUID, q, offset, limit, customSortBy, customSortOrder, order);
		totalElements = incomeRepositoryCustomImpl.countIncomes(user.getId(), startDate, endDate,
				categoryUUID, q);

		totalPages = (int) Math.ceil((double) totalElements / limit);
		String displayCurrency = user.getCurrency();
		return new IncomeResList(mapIncomesToResponse(incomes, displayCurrency),
				totalPages, totalElements, page);
	}

	public String exportIncomesToCSV(String userId, LocalDateTime startDate, LocalDateTime endDate) throws IOException {
		UUID userIdUUID = UUID.fromString(userId);
		User user = userService.GetActiveUserById(userId);
		List<Income> incomes = incomeRepository.findByUserIdAndTimeFrameAsc(userIdUUID, startDate, endDate);
		String displayCurrency = user == null ? BASE_CURRENCY : user.getCurrency();
		BigDecimal displayRate = resolveUsdRate(displayCurrency);

		StringWriter sw = new StringWriter();
		CSVWriter writer = new CSVWriter(sw);

		writer.writeNext(new String[]{"Date", "Description", "Amount (in " + displayCurrency + ")", "Category"});

		for (Income income : incomes) {
			BigDecimal displayAmount = income.getBaseCurrencyAmount() == null
					? normalizeAmount(income.getAmount())
					: income.getBaseCurrencyAmount().multiply(displayRate).setScale(2, RoundingMode.HALF_UP);
			writer.writeNext(new String[]{
					income.getIncomeDate().toString(),
					income.getDescription(),
					displayAmount.toString(),
					income.getCategory().getName()
			});
		}
		writer.close();
		return sw.toString();
	}
}
