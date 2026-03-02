package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.DailyIncome;
import com.example.expensely_backend.dto.IncomeResList;
import com.example.expensely_backend.dto.IncomeResponse;
import com.example.expensely_backend.dto.MonthlyCategoryIncome;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Income;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.CategoryRepository;
import com.example.expensely_backend.repository.IncomeRepository;
import com.example.expensely_backend.repository.IncomeRepositoryCustomImpl;
import com.example.expensely_backend.utils.FormatDate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IncomeService {

	private final IncomeRepository incomeRepository;
	private final CategoryService categoryService;
	private final UserService userService;
	private final IncomeRepositoryCustomImpl incomeRepositoryCustomImpl;
	private final CategoryRepository categoryRepository;

	public IncomeService(IncomeRepository incomeRepository,
	                     CategoryService categoryService,
	                     UserService userService,
	                     IncomeRepositoryCustomImpl incomeRepositoryCustomImpl,
	                     CategoryRepository categoryRepository) {
		this.incomeRepository = incomeRepository;
		this.categoryService = categoryService;
		this.userService = userService;
		this.incomeRepositoryCustomImpl = incomeRepositoryCustomImpl;
		this.categoryRepository = categoryRepository;
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

		return incomeRepository.save(income);
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

	public List<Income> getIncomesByUserId(String userId) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		return incomeRepository.findByUserId(user.getId());
	}

	public List<Income> getIncomesByCategoryIdAndUserID(String categoryId, String userId) {
		Category category = categoryService.getCategoryById(categoryId);
		if (!globals.TYPE_INCOME.equals(category.getType())) {
			throw new IllegalArgumentException("Category must be income type");
		}
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		return incomeRepository.findByCategoryIdAndUserId(category.getId(), user.getId());
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
			oldIncome.setAmount(income.getAmount());
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
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}

		List<Income> incomes;
		if (order == null || order.equalsIgnoreCase("desc")) {
			incomes = incomeRepository.findByUserIdAndTimeFrameDesc(user.getId(), startDate, endDate);
		} else if (order.equalsIgnoreCase("asc")) {
			incomes = incomeRepository.findByUserIdAndTimeFrameAsc(user.getId(), startDate, endDate);
		} else {
			throw new IllegalArgumentException("Order must be 'asc' or 'desc'");
		}

		return incomes.stream().map(IncomeResponse::new).collect(Collectors.toList());
	}

	public List<MonthlyCategoryIncome> getMonthlyCategoryIncome(String userId, LocalDateTime startDate, LocalDateTime endDate) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}

		return incomeRepository.findMonthlyCategoryIncomeByUserId(user.getId(), startDate, endDate);
	}

	public List<DailyIncome> getDailyIncome(String userId, LocalDateTime startDate, LocalDateTime endDate) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		return incomeRepository.findDailyIncomeByUserIdAndTimeFrame(user.getId(), startDate, endDate);
	}

	public LinkedHashMap<String, Double> getMonthlyIncomeFromTillTo(String userId, int count,
	                                                                globals.TimeFrame type) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
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
		LocalDateTime startDate, endDate;
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
		YearMonth date_ym = YearMonth.of(date.getYear(), date.getMonth());
		endDate = LocalDateTime.of(date.getYear(), date.getMonth(), date_ym.lengthOfMonth(), 23, 59, 59);

		return incomeRepositoryCustomImpl.getMonthlyIncomeFromTillTo(user.getId(), startDate, endDate);
	}

	public LinkedHashMap<String, java.util.Map<String, Double>> getMonthlyCategoryIncomeFromTillTo(String userId, int count,
	                                                                                               globals.TimeFrame type) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
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
		LocalDateTime startDate, endDate;
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
		YearMonth date_ym = YearMonth.of(date.getYear(), date.getMonth());
		endDate = LocalDateTime.of(date.getYear(), date.getMonth(), date_ym.lengthOfMonth(), 23, 59, 59);

		List<MonthlyCategoryIncome> dbRes =
				incomeRepositoryCustomImpl.getMonthlyCategoryIncomeFromTillTo(user.getId(), startDate, endDate);

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

			if (category != null) {
				monthlyCategoryIncome
						.computeIfAbsent(month, k -> new LinkedHashMap<>())
						.merge(category, amount, Double::sum);
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
		return new IncomeResList(incomes.stream().map(IncomeResponse::new).collect(Collectors.toList()),
				totalPages, totalElements, page);
	}
}
