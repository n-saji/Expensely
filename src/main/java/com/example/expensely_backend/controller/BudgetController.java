package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.BudgetResponse;
import com.example.expensely_backend.dto.BudgetResponseList;
import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.service.BudgetService;
import com.example.expensely_backend.service.ResourceAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
	private final BudgetService budgetService;
	private final ResourceAccess access;

	public BudgetController(BudgetService budgetService, ResourceAccess access) {
		this.budgetService = budgetService;
		this.access = access;
	}

	@PostMapping("/create")
	public ResponseEntity<?> create(Authentication authentication,
	                                @RequestBody Budget budget) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body(new BudgetResponse("Unauthorized", null));
		}
		if (budget.getAmountLimit() == null || budget.getAmountLimit().intValue() <= 0) {
			return ResponseEntity.badRequest().body(new BudgetResponse("Budget limit must not be null", null));
		}

		if (budget.getCategory() == null || budget.getCategory().getId() == null) {
			return ResponseEntity.badRequest().body(new BudgetResponse("Category must not be null", null));
		}
		access.category(authentication, budget.getCategory().getId().toString());

		try {
			Budget budgetRes = budgetService.save(userId, budget);
			budgetRes.setUser(null);
			budgetRes.setCategory(null);
			return ResponseEntity.ok(new BudgetResponse(budgetRes, "",
					"Successfully " +
							"created"));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new BudgetResponse(e.getMessage(), null));
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<Budget> getBudgetById(Authentication authentication, @PathVariable String id) {
		access.budget(authentication, id);
		try {
			Budget budget = budgetService.findById(id);
			return ResponseEntity.ok(budget);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(null);
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteBudgetById(Authentication authentication, @PathVariable String id) {
		access.budget(authentication, id);
		try {
			budgetService.softDeleteById(id);
			return ResponseEntity.status(204).body(new BudgetResponse("", "Successfully deleted"));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new BudgetResponse(e.getMessage(), null));
		}
	}

	@GetMapping("/all")
	public ResponseEntity<?> getAllBudgets(Authentication authentication) {
		access.admin(authentication);
		try {
			return ResponseEntity.ok(new BudgetResponseList(budgetService.findAll()));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new BudgetResponse(e.getMessage(), null));
		}
	}

	@GetMapping("/user/{userId}")
	public ResponseEntity<?> getBudgetsByUserId(Authentication authentication, @PathVariable String userId) {
		access.sameUser(authentication, userId);
		try {
			return ResponseEntity.ok(budgetService.getBudgetsByUserId(userId));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> updateBudget(Authentication authentication,
	                                      @PathVariable String id,
	                                      @RequestBody Budget budget) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body(new BudgetResponse("Unauthorized", null));
		}
		if (id == null) {
			return ResponseEntity.badRequest().body(new BudgetResponse(
					"budget id must not be empty", null));
		}
		access.budget(authentication, id);
		if (budget.getCategory() != null && budget.getCategory().getId() != null)
			access.category(authentication, budget.getCategory().getId().toString());
		try {
			Budget budgetRes = budgetService.updateBudget(userId, id, budget);
			budgetRes.setUser(null);
			budgetRes.setCategory(null);
			return ResponseEntity.ok(new BudgetResponse(budgetRes, "", "Budget" +
					" updated successfully"));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new BudgetResponse(e.getMessage(), null));
		}
	}

	@GetMapping("/available-categories")
	public ResponseEntity<?> getAvailableCategories(Authentication authentication) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			return ResponseEntity.ok(budgetService.getCategoriesWithoutActiveBudget(userId));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}


}
