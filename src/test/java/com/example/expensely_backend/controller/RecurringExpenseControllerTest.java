package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.RecurringExpenseDTO;
import com.example.expensely_backend.service.RecurringExpenseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseControllerTest {

    @Mock
    private RecurringExpenseService recurringExpenseService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RecurringExpenseController controller;

    @Test
    void updateReturnsBadRequestForInvalidNotificationDays() {
        RecurringExpenseDTO request = new RecurringExpenseDTO();
        when(authentication.getPrincipal()).thenReturn("user-id");
        doThrow(new IllegalArgumentException("Notification days must be between 1 and 365"))
                .when(recurringExpenseService).updateRecurringExpense("expense-id", request);

        ResponseEntity<?> response = controller.updateRecurringExpense(authentication, "expense-id", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Notification days must be between 1 and 365");
    }
}
