package com.example.expensely_backend.controller;


import com.example.expensely_backend.dto.AuthResponse;
import com.example.expensely_backend.dto.ExpenseOverview;
import com.example.expensely_backend.dto.UserRes;
import com.example.expensely_backend.model.Expense;
import com.example.expensely_backend.service.CategoryService;
import com.example.expensely_backend.service.ExpenseService;
import com.example.expensely_backend.utils.FormatDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CategoryService categoryService;
    private final FormatDate formatDate = new FormatDate();

    public ExpenseController(ExpenseService expenseService , CategoryService categoryService) {
        this.expenseService = expenseService;
        this.categoryService = categoryService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createExpense(@RequestBody Expense expense) {
        // Logic to create an expense
        try {
            if (expense.getExpenseDate() == null) {
                expense.setExpenseDate(LocalDateTime.now());
            }
            expenseService.save(expense);
            return ResponseEntity.ok(new AuthResponse("Expense created successfully!", null, null, ""));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse("Expense creation failed!", null, null, e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Expense> getExpenseById(@PathVariable String id) {
        try {
            Expense expense = expenseService.getExpenseById(id);
            return ResponseEntity.ok(expense);
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
    public ResponseEntity<?> updateExpense(@PathVariable String id, @RequestBody Expense updatedExpense) {
        try {
            Expense existingExpense = expenseService.getExpenseById(id);
            if (existingExpense == null) {
                return ResponseEntity.badRequest().body(new AuthResponse("Expense not found!", null, null, ""));
            }

            // Save updated expense
            expenseService.updateExpense(updatedExpense);
            return ResponseEntity.ok(new AuthResponse("Expense updated successfully!", null, null, ""));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse("Expense update failed!", null, null, e.getMessage()));
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

        startDate = FormatDate.formatStartDate(startDate);
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
            @RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        int year = LocalDateTime.now().getYear();
        int month = LocalDateTime.now().getMonthValue();
        startDate = FormatDate.formatStartDate(startDate);
        endDate = FormatDate.formatEndDate(endDate);
        try {
            return ResponseEntity.ok(
                    new ExpenseOverview(expenseService.getExpenseByUserIdAndStartDateAndEndDate(userId, startDate, endDate, "desc"),
                            userId,expenseService.getMonthlyCategoryExpense(userId,startDate,endDate),
                            categoryService.getCategoriesByUserId(userId,"expense"),
                            expenseService.getDailyExpense(userId,LocalDateTime.of(year,month,1,0,0),endDate)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/user/{userId}/bulk-delete")
    public ResponseEntity<?> bulkDeleteExpensesByUserId(@PathVariable String userId, @RequestBody List<Expense> expenses) {
        try {
            expenseService.deleteBuUserIDAndExpenseIds(userId, expenses);
            return ResponseEntity.ok(new AuthResponse("Bulk delete expenses successfully!", null, null, ""));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse("Bulk delete expenses failed!", null, null, e.getMessage()));
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
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {

        startDate = FormatDate.formatStartDate(startDate);
        endDate = FormatDate.formatEndDate(endDate);
        if (order != null && !order.equals("asc") && !order.equals("desc")) {
            return ResponseEntity.badRequest().body(new UserRes(null, "Error: Order must be 'asc' or 'desc'"));
        }
        try {
            return ResponseEntity.ok(expenseService.fetchExpensesWithConditions(userId, startDate, endDate, order, categoryId, page, limit));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
        }

    }

    @GetMapping("/user/{userId}/export")
    public ResponseEntity<?> exportExpenses(@PathVariable String userId,
                                             @RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
                                             @RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        startDate = FormatDate.formatStartDate(startDate);
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

}
