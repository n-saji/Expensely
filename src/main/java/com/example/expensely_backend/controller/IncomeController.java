package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.AuthResponse;
import com.example.expensely_backend.dto.IncomeOverview;
import com.example.expensely_backend.dto.UserRes;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.Income;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.CategoryService;
import com.example.expensely_backend.service.IncomeService;
import com.example.expensely_backend.service.UserService;
import com.example.expensely_backend.utils.FormatDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/incomes")
public class IncomeController {

	private final IncomeService incomeService;
	private final UserService userService;
	private final CategoryService categoryService;

	public IncomeController(IncomeService incomeService, UserService userService, CategoryService categoryService) {
		this.incomeService = incomeService;
		this.userService = userService;
		this.categoryService = categoryService;
	}

	@PostMapping("/create")
	public ResponseEntity<?> createIncome(Authentication authentication, @RequestBody Income income) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			if (income.getIncomeDate() == null) {
				income.setIncomeDate(LocalDateTime.now());
			}
			User user = userService.GetActiveUserById(userId);
			income.setUser(user);
			Income savedIncome = incomeService.save(income);
			return ResponseEntity.ok(new AuthResponse("Income created successfully!", savedIncome.getId().toString(), ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Income creation failed!", null, e.getMessage()));
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<Income> getIncomeById(Authentication authentication, @PathVariable String id) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body(null);
		}
		try {
			Income income = incomeService.getIncomeByIdForUser(id, userId);
			income.setUser(null);
			if (income.getCategory() != null) {
				income.getCategory().setUser(null);
			}
			return ResponseEntity.ok(income);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(null);
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<String> deleteIncomeById(Authentication authentication, @PathVariable String id) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			incomeService.deleteIncomeByIdForUser(id, userId);
			return ResponseEntity.ok("Income deleted successfully!");
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	@GetMapping("/user")
	public ResponseEntity<?> getIncomesByUserId(Authentication authentication) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			List<Income> incomes = incomeService.getIncomesByUserId(userId);
			incomes.forEach(income -> {
				income.setUser(null);
				if (income.getCategory() != null) {
					income.getCategory().setUser(null);
				}
			});
			return ResponseEntity.ok(incomes);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	@GetMapping("/user/category/{categoryId}")
	public ResponseEntity<?> getIncomesByCategoryIdAndUserID(Authentication authentication,
	                                                         @PathVariable String categoryId) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			List<Income> incomes = incomeService.getIncomesByCategoryIdAndUserID(categoryId, userId);
			incomes.forEach(income -> {
				income.setUser(null);
				if (income.getCategory() != null) {
					income.getCategory().setUser(null);
				}
			});
			return ResponseEntity.ok(incomes);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@PutMapping("/update/{id}")
	public ResponseEntity<?> updateIncome(Authentication authentication, @PathVariable String id, @RequestBody Income updatedIncome) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			Income existingIncome = incomeService.getIncomeByIdForUser(id, userId);
			if (existingIncome == null) {
				return ResponseEntity.badRequest().body(new AuthResponse("Income not found!", null, ""));
			}
			updatedIncome.setId(existingIncome.getId());
			updatedIncome.setUser(existingIncome.getUser());
			Income savedIncome = incomeService.updateIncome(updatedIncome);
			return ResponseEntity.ok(new AuthResponse("Income updated successfully!", savedIncome.getId().toString(), ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Income update failed!", null, e.getMessage()));
		}
	}

	@GetMapping("/overview")
	public ResponseEntity<?> getIncomeOverview(Authentication authentication,
	                                           @RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
	                                           @RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
	                                           @RequestParam(value = "req_year", required = false) Integer reqYear,
	                                           @RequestParam(value = "req_month", required = false) Integer reqMonth,
	                                           @RequestParam(value = "req_month_year", required = false) Integer reqMonthYear) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		int year = LocalDateTime.now().getYear();
		int month = LocalDateTime.now().getMonthValue();
		reqYear = reqYear == null ? year : reqYear;
		reqMonthYear = reqMonthYear == null ? year : reqMonthYear;
		reqMonth = reqMonth == null ? month : reqMonth;

		YearMonth reqYm = YearMonth.of(reqMonthYear, reqMonth);
		LocalDateTime reqStart = LocalDateTime.of(reqMonthYear, reqMonth, 1, 0, 0);
		LocalDateTime reqEnd = reqYm.atEndOfMonth().atTime(23, 59, 59);

		startDate = FormatDate.formatStartDate(startDate, true);
		endDate = FormatDate.formatEndDate(endDate);

		LocalDateTime reqStartYear = LocalDateTime.of(reqYear, 1, 1, 0, 0);
		LocalDateTime reqEndYear = LocalDateTime.of(reqYear, 12, 31, 23, 59, 59);

		try {
			return ResponseEntity.ok().body(new IncomeOverview(
					incomeService.getIncomeByUserIdAndStartDateAndEndDate(userId, startDate, endDate, "desc"),
					incomeService.getIncomeByUserIdAndStartDateAndEndDate(userId, reqStartYear, reqEndYear, "desc"),
					incomeService.getIncomeByUserIdAndStartDateAndEndDate(userId, reqStart, reqEnd, "desc"),
					userId,
					incomeService.getMonthlyCategoryIncome(userId, reqStartYear, reqEndYear),
					categoryService.getCategoriesByUserId(userId, globals.TYPE_INCOME),
					incomeService.getDailyIncome(userId, reqStart, reqEnd),
					reqMonth,
					incomeService.getFirstIncome(userId),
					incomeService.getTotalIncomeForMonth(month == 1 ? year - 1 : year,
							month == 1 ? 12 : month - 1, userId)));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@GetMapping("/monthly")
	public ResponseEntity<?> getIncomeByMonthRange(Authentication authentication,
	                                               @RequestParam(name = "count", defaultValue = "6") int count,
	                                               @RequestParam(name = "type", defaultValue = "MONTH") globals.TimeFrame type) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			return ResponseEntity.ok(incomeService.getMonthlyIncomeFromTillTo(userId, count, type));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@GetMapping("/monthly/category")
	public ResponseEntity<?> getIncomeByMonthCategoryRange(Authentication authentication,
	                                                       @RequestParam(name = "count", defaultValue = "6") int count,
	                                                       @RequestParam(name = "type", defaultValue = "MONTH") globals.TimeFrame type) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			return ResponseEntity.ok(incomeService.getMonthlyCategoryIncomeFromTillTo(userId, count, type));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@GetMapping("/fetch-with-conditions")
	public ResponseEntity<?> fetchWithConditions(Authentication authentication,
	                                             @RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
	                                             @RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
	                                             @RequestParam(value = "order", required = false, defaultValue = "desc") String order,
	                                             @RequestParam(value = "category_id", required = false) String categoryId,
	                                             @RequestParam(value = "page", required = false, defaultValue = "1") int page,
	                                             @RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
	                                             @RequestParam(value = "q", required = false) String q,
	                                             @RequestParam(value = "sort_by", required = false) String customSortBy,
	                                             @RequestParam(value = "sort_order", required = false) String customSortOrder) {

		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}

		startDate = FormatDate.formatStartDate(startDate, false);
		endDate = FormatDate.formatEndDate(endDate);
		if (q == null) q = "";
		if (order != null && !order.equals("asc") && !order.equals("desc")) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: Order must be 'asc' or 'desc'"));
		}
		if (customSortBy != null) {
			if (!customSortBy.equals("amount") && !customSortBy.equals("incomeDate") &&
					!customSortBy.equals("description") && !customSortBy.equals("category")) {
				return ResponseEntity.badRequest().body(new UserRes(null, "Error: Invalid sort column"));
			}
		}
		try {
			return ResponseEntity.ok(incomeService.fetchIncomesWithConditions(userId, startDate
					, endDate, order, categoryId, page, limit, q, customSortBy, customSortOrder));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@PostMapping("/bulk-delete")
	public ResponseEntity<?> bulkDeleteIncomes(Authentication authentication, @RequestBody List<Income> incomes) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			incomeService.deleteByUserIDAndIncomeIds(userId, incomes);
			return ResponseEntity.ok(new AuthResponse("Bulk delete incomes successfully!", null, ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Bulk delete incomes failed!", null, e.getMessage()));
		}
	}
}
