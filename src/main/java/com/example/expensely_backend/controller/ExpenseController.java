package com.example.expensely_backend.controller;


import com.example.expensely_backend.dto.*;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.service.*;
import com.example.expensely_backend.utils.FormatDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

	private final ExpenseService expenseService;
	private final ExpenseFilesService expenseFilesService;

	public ExpenseController(ExpenseService expenseService,

	                         ExpenseFilesService expenseFilesService) {
		this.expenseService = expenseService;
		this.expenseFilesService = expenseFilesService;
	}

	@PostMapping("/create")
	public ResponseEntity<?> createExpense(@RequestBody Transaction expense) {
		// Logic to create an expense
		try {
			if (expense.getTransactionDate() == null) {
				expense.setTransactionDate(LocalDateTime.now());
			}
			Transaction exp = expenseService.save(expense);
			return ResponseEntity.ok(expenseService.getExpenseResponseById(exp.getId().toString()));

		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Expense creation failed!", null, e.getMessage()));
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getExpenseById(@PathVariable String id) {
		try {
			return ResponseEntity.ok(expenseService.getExpenseResponseById(id));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(null);
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<String> deleteExpenseById(@PathVariable String id) {
		try {
			expenseService.deleteExpenseById(id);
			return ResponseEntity.ok("Expense deleted successfully!");
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}


	@GetMapping("/user/{userId}")
	public ResponseEntity<?> getExpensesByUserId(@PathVariable String userId) {
		try {
			return ResponseEntity.ok(expenseService.getExpensesByUserId(userId));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	@PutMapping("/update/{id}")
	public ResponseEntity<?> updateExpense(@PathVariable String id, @RequestBody Transaction updatedExpense) {
		try {
			Transaction existingExpense = expenseService.getExpenseById(id);
			if (existingExpense == null) {
				return ResponseEntity.badRequest().body(new AuthResponse("Expense not found!", null, ""));
			}

			// Save updated expense
			Transaction exp =
					expenseService.updateExpense(updatedExpense);
			return ResponseEntity.ok(new AuthResponse("Expense updated " +
					"successfully!", exp.getId().toString(), ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Expense update failed!", null, e.getMessage()));
		}
	}

	@GetMapping("/user/{userId}/category/{categoryId}")
	public ResponseEntity<?> getExpensesByCategoryIdAndUserID(@PathVariable String categoryId, @PathVariable String userId) {
		try {
			return ResponseEntity.ok(expenseService.getExpensesByCategoryIdAndUserID(categoryId, userId));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));

		}
	}

	@GetMapping("/user/{userId}/timeframe")
	public ResponseEntity<?> getExpensesByUserIdAndTimeFrame(
			@PathVariable String userId,
			@RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
			@RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
			@RequestParam(value = "order", required = false, defaultValue = "desc") String order) {

		startDate = FormatDate.formatStartDate(startDate, false);
		endDate = FormatDate.formatEndDate(endDate);
		try {
			return ResponseEntity.ok(expenseService.getExpenseByUserIdAndStartDateAndEndDate(userId, startDate, endDate, order));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@GetMapping("/user/{userId}/overview")
	public ResponseEntity<?> getExpensesOverviewByUserIdAndTimeFrame(
			@PathVariable String userId,
			@RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
			@RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
			@RequestParam(value = "req_year", required = false) Integer req_year,
			@RequestParam(value = "req_month", required = false) Integer req_month,
			@RequestParam(value = "req_month_year", required = false) Integer req_month_year) {

		int year = LocalDateTime.now().getYear();
		int month = LocalDateTime.now().getMonthValue();
		req_year = req_year == null ? year : req_year;
		req_month_year = req_month_year == null ? year : req_month_year;
		req_month = req_month == null ? month : req_month;

		// this is for monthly view
		YearMonth req_ym = YearMonth.of(req_month_year, req_month);
		LocalDateTime req_start = LocalDateTime.of(req_month_year, req_month, 1, 0, 0);
		LocalDateTime req_end = req_ym.atEndOfMonth().atTime(23, 59, 59);

		// this is for current year view
		startDate = FormatDate.formatStartDate(startDate, true);
		endDate = FormatDate.formatEndDate(endDate);

		// this is for requested yearly view
		LocalDateTime req_start_year = LocalDateTime.of(req_year, 1, 1, 0, 0);
		LocalDateTime req_end_year = LocalDateTime.of(req_year, 12, 31, 23, 59, 59);

		try {

			return ResponseEntity.ok().body(
					expenseService.getExpensesOverviewByUserIdAndTimeFrame(userId, startDate, endDate, year, month, req_start_year, req_end_year, req_start, req_end, req_month));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@PostMapping("/user/{userId}/bulk-delete")
	public ResponseEntity<?> bulkDeleteExpensesByUserId(@PathVariable String userId, @RequestBody List<Transaction> expenses) {
		try {
			expenseService.deleteBuUserIDAndExpenseIds(userId, expenses);
			return ResponseEntity.ok(new AuthResponse("Bulk delete expenses successfully!", null, ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Bulk delete expenses failed!", null, e.getMessage()));
		}
	}

	@GetMapping("/user/{userId}/fetch-with-conditions")
	public ResponseEntity<?> fetchWithConditions(
			@PathVariable String userId,
			@RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
			@RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
			@RequestParam(value = "order", required = false, defaultValue = "desc") String order,
			@RequestParam(value = "category_id", required = false) String categoryId,
			@RequestParam(value = "page", required = false, defaultValue = "1") int page,
			@RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
			@RequestParam(value = "q", required = false) String q,
			@RequestParam(value = "sort_by", required = false) String customSortBy,
			@RequestParam(value = "sort_order", required = false) String customSortOrder) {


		startDate = FormatDate.formatStartDate(startDate, false);
		endDate = FormatDate.formatEndDate(endDate);
		if (q == null) q = "";
		if (order != null && !order.equals("asc") && !order.equals("desc")) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: Order must be 'asc' or 'desc'"));
		}
		if (customSortBy != null) {
			if (!customSortBy.equals("amount") && !customSortBy.equals("expenseDate") &&
					!customSortBy.equals("description") && !customSortBy.equals("category")) {
				return ResponseEntity.badRequest().body(new UserRes(null, "Error: Invalid sort column"));
			}
			if (customSortBy.equals("expenseDate")) {
				customSortBy = "transactionDate";
			}
			if (customSortBy.equals("amount")) {
				customSortBy = "baseCurrencyAmount";
			}
		}
		try {
			return ResponseEntity.ok(expenseService.fetchExpensesWithConditions(userId, startDate
					, endDate, order, categoryId, page, limit, q, customSortBy, customSortOrder));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}

	}

	@GetMapping("/user/{userId}/export")
	public ResponseEntity<?> exportExpenses(@PathVariable String userId,
	                                        @RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
	                                        @RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
		startDate = FormatDate.formatStartDate(startDate, false);
		endDate = FormatDate.formatEndDate(endDate);
		try {
			String csvData = expenseService.exportExpensesToCSV(userId, startDate, endDate);
			return ResponseEntity.ok()
					.header("Content-Disposition", "attachment; filename=\"expenses.csv\"")
					.contentType(MediaType.parseMediaType("text/csv"))
					.body(csvData.getBytes(StandardCharsets.UTF_8));

		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@PostMapping(value = "/bulk_upload/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> validateExcelFile(Authentication authentication,
	                                           @RequestParam("file") MultipartFile file) {
		String userId = (String) authentication.getPrincipal();
		if (file == null || file.isEmpty()) {
			return ResponseEntity.badRequest().body("Please select a file to upload");
		}
		try {

			BulkValidationResponse response =
					expenseFilesService.validateFile(file, userId);


			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}


	}

	@GetMapping("/bulk_upload/upload")
	public ResponseEntity<?> uploadExpenseWithFileId(Authentication authentication, @RequestParam("file_id") String fileId) {
		String userId = (String) authentication.getPrincipal();
		if (fileId.isEmpty()) {
			return ResponseEntity.badRequest().body("Please select a file to upload");
		}

		try {
			return ResponseEntity.ok().body(expenseService.BulkInsertExpensesFromFile(userId,
					fileId));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@GetMapping("/monthly")
	public ResponseEntity<?> getExpenseByMonthRange(Authentication authentication,
	                                                @RequestParam(name = "count", defaultValue = "6") int count,
	                                                @RequestParam(name = "type", defaultValue = "MONTH") globals.TimeFrame type
	) {

		String userId = (String) authentication.getPrincipal();

		try {
			return ResponseEntity.ok(expenseService.getMonthlyExpenseFromTillTo(userId, count,
					type));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@GetMapping("/monthly/category")
	public ResponseEntity<?> getExpenseByMonthCategoryRange(Authentication authentication,
	                                                        @RequestParam(name = "count", defaultValue = "6") int count,
	                                                        @RequestParam(name = "type", defaultValue = "MONTH") globals.TimeFrame type
	) {

		String userId = (String) authentication.getPrincipal();

		try {
			return ResponseEntity.ok(expenseService.getMonthlyCategoryExpenseFromTillTo(userId,
					count,
					type));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@GetMapping("/get-presigned-url")
	public ResponseEntity<?> getPresignedURL(Authentication authentication,
	                                         @RequestParam(name = "expenseId") String expenseID,
	                                         @RequestParam(name =
			                                         "contentType") String contentType,
	                                         @RequestParam(name = "fileName") String fileName
	) {

		String userId = (String) authentication.getPrincipal();

		try {
			Transaction expense = expenseService.getExpenseById(expenseID);
			if (expense == null) {
				return ResponseEntity.badRequest().body("Expense not found");
			}
			fileName = expenseID + "-" + fileName;
			Map<String, String> resp =
					expenseService.GenerateS3PresignedURLForExpense(userId,
							fileName,
							contentType);
			return ResponseEntity.ok().body(new S3Resp(resp.get("presignedURL"), resp.get("fileURL")));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@PutMapping("/update-expense-attachment-url/eid/{eid}")
	public ResponseEntity<?> updateExpenseAttachment(Authentication authentication,
	                                                 @PathVariable(name = "eid") String expenseID,
	                                                 @RequestBody S3Resp url

	) {

		String userId = (String) authentication.getPrincipal();
		try {
			expenseService.UpdateExpenseAttachment(userId, expenseID, url.getUrl());
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
		return ResponseEntity.ok().body("Expense attachment updated successfully");
	}


	@GetMapping("/get-download-url/eid/{eid}")
	public ResponseEntity<?> getDownloadURL(Authentication authentication,
	                                        @PathVariable String eid
	) {

		String userId = (String) authentication.getPrincipal();
		try {

			return ResponseEntity.ok().body(new S3Resp("",
					expenseService.GenerateDownloadURLForExpense(eid)));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@DeleteMapping("/delete-attachment/eid/{eid}")
	public ResponseEntity<?> deleteExpenseAttachment(Authentication authentication,
	                                                 @PathVariable String eid
	) {
		String userId = (String) authentication.getPrincipal();
		try {
			expenseService.DeleteExpenseAttachment(userId, eid);
			return ResponseEntity.ok().body("Expense attachment deleted successfully");
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

}
