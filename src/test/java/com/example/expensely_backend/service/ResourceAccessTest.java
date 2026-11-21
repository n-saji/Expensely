package com.example.expensely_backend.service;

import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceAccessTest {
	private final CategoryRepository categories = mock(CategoryRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final ResourceAccess access = new ResourceAccess(categories, mock(TransactionRepository.class),
			mock(BudgetRepository.class), mock(RecurringExpenseRepository.class), users);

	@Test
	void foreignCategoryAndClaimedUserAreDenied() {
		UUID callerId = UUID.randomUUID();
		UUID otherId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();
		User owner = mock(User.class);
		when(owner.getId()).thenReturn(otherId);
		Category category = mock(Category.class);
		when(category.getUser()).thenReturn(owner);
		when(categories.findById(categoryId)).thenReturn(Optional.of(category));
		var authentication = new UsernamePasswordAuthenticationToken(callerId.toString(), null);
		assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
				() -> access.category(authentication, categoryId.toString())).getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
				() -> access.sameUser(authentication, otherId.toString())).getStatusCode());
	}

	@Test
	void ordinaryUserCannotUseAdminRoute() {
		UUID callerId = UUID.randomUUID();
		User caller = mock(User.class);
		when(caller.getIsAdmin()).thenReturn(false);
		when(users.findById(callerId)).thenReturn(Optional.of(caller));
		assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
				() -> access.admin(new UsernamePasswordAuthenticationToken(callerId.toString(), null))).getStatusCode());
	}
}
