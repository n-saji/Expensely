package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.RecurringExpenseDTO;
import com.example.expensely_backend.service.RecurringExpenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recurring-expenses")
public class RecurringExpenseController {

	private final RecurringExpenseService recurringExpenseService;

	public RecurringExpenseController(RecurringExpenseService recurringExpenseService) {
		this.recurringExpenseService = recurringExpenseService;
	}

	@GetMapping("/fetch-all")
	public ResponseEntity<?> getRecurringExpensesForUser(Authentication authentication) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			return ResponseEntity.ok(recurringExpenseService.findRecurringExpenseByUserId(userId));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(404).body(e.getMessage());
		}
	}

	@PostMapping("/create")
	public ResponseEntity<?> createRecurringExpense(Authentication authentication, @RequestBody RecurringExpenseDTO recurringExpense) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			recurringExpense.setUserId(userId);
			recurringExpenseService.createRecurringExpense(recurringExpense);
			return ResponseEntity.ok("Recurring expense created successfully");
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(400).body(e.getMessage());
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getRecurringExpenseById(Authentication authentication, @PathVariable String id) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			return ResponseEntity.ok(recurringExpenseService.findRecurringExpenseById(id));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(404).body(e.getMessage());
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteRecurringExpenseById(Authentication authentication, @PathVariable String id) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			recurringExpenseService.deleteRecurringExpenseById(id);
			return ResponseEntity.ok("Recurring expense deleted successfully");
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(404).body(e.getMessage());
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> updateRecurringExpense(Authentication authentication, @PathVariable String id, @RequestBody RecurringExpenseDTO recExpenseDTO) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			recurringExpenseService.updateRecurringExpense(id, recExpenseDTO);
			return ResponseEntity.ok("Recurring expense updated successfully");
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(404).body(e.getMessage());
		}
	}

	@PutMapping("/deactivate/{id}")
	public ResponseEntity<?> deactivateRecurringExpense(Authentication authentication, @PathVariable String id) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			recurringExpenseService.deactivateRecurringExpense(id);
			return ResponseEntity.ok("Recurring expense deactivated successfully");
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(404).body(e.getMessage());
		}
	}

	@PutMapping("/activate/{id}")
	public ResponseEntity<?> activateRecurringExpense(Authentication authentication, @PathVariable String id) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			recurringExpenseService.activateRecurringExpense(id);
			return ResponseEntity.ok("Recurring expense activated successfully");
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(404).body(e.getMessage());
		}
	}
}
