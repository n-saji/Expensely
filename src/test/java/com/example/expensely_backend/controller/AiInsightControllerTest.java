package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.AiInsightStatusDto;
import com.example.expensely_backend.dto.AiInsightsResponse;
import com.example.expensely_backend.service.AiInsightService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInsightControllerTest {
    @Mock
    private AiInsightService aiInsightService;

    @InjectMocks
    private AiInsightController controller;

    @Test
    void derivesUserIdentityFromAuthenticationPrincipal() {
        UUID authenticatedUser = UUID.randomUUID();
        var expected = new AiInsightsResponse(
                new AiInsightStatusDto(
                        "NOT_AVAILABLE", true, LocalDate.of(2026, 11, 1), null, null, null
                ),
                List.of()
        );
        when(aiInsightService.getCurrent(authenticatedUser)).thenReturn(expected);

        var response = controller.getInsights(
                new TestingAuthenticationToken(authenticatedUser.toString(), null)
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(aiInsightService).getCurrent(authenticatedUser);
    }

    @Test
    void rejectsMissingAuthentication() {
        assertEquals(HttpStatus.UNAUTHORIZED, controller.getInsights(null).getStatusCode());
    }
}
