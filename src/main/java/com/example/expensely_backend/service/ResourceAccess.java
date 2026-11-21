package com.example.expensely_backend.service;

import com.example.expensely_backend.model.*;
import com.example.expensely_backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class ResourceAccess {
	private final CategoryRepository categories;
	private final TransactionRepository transactions;
	private final BudgetRepository budgets;
	private final RecurringExpenseRepository recurring;
	private final UserRepository users;

	public ResourceAccess(CategoryRepository categories, TransactionRepository transactions,
	                      BudgetRepository budgets, RecurringExpenseRepository recurring, UserRepository users) {
		this.categories = categories;
		this.transactions = transactions;
		this.budgets = budgets;
		this.recurring = recurring;
		this.users = users;
	}

	public UUID userId(Authentication authentication) {
		if (authentication == null || authentication.getPrincipal() == null)
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
		try { return UUID.fromString(authentication.getPrincipal().toString()); }
		catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid principal"); }
	}

	public void sameUser(Authentication authentication, String claimedUserId) {
		if (!userId(authentication).toString().equals(claimedUserId))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account access denied");
	}

	public void category(Authentication authentication, String id) {
		Category category = categories.findById(parseId(id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		owned(authentication, category.getUser().getId());
	}

	public void transaction(Authentication authentication, String id) {
		Transaction transaction = transactions.findById(parseId(id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		owned(authentication, transaction.getUser().getId());
	}

	public void budget(Authentication authentication, String id) {
		Budget budget = budgets.findById(parseId(id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		owned(authentication, budget.getUser().getId());
	}

	public void recurring(Authentication authentication, String id) {
		RecurringExpense expense = recurring.findById(parseId(id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		owned(authentication, expense.getUser().getId());
	}

	public void owned(Authentication authentication, UUID ownerId) {
		if (!userId(authentication).equals(ownerId))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource access denied");
	}

	public void admin(Authentication authentication) {
		User user = users.findById(userId(authentication)).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
		if (!Boolean.TRUE.equals(user.getIsAdmin())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access denied");
	}

	private UUID parseId(String id) {
		if (id == null || id.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ID");
		try { return UUID.fromString(id); }
		catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ID"); }
	}
}
