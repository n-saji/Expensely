package com.example.expensely_backend.utils;

import com.example.expensely_backend.model.ApiRequestLog;
import com.example.expensely_backend.service.DbLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiRequestLoggingFilterTest {
	@Test
	void authTokensNeverEnterRequestLog() throws Exception {
		DbLogService logs = mock(DbLogService.class);
		ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(logs);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mobile/auth/refresh?refreshToken=secret-query");
		request.setQueryString("refreshToken=secret-query");
		request.addHeader("Authorization", "Bearer secret-access");
		request.addHeader("Cookie", "refreshToken=secret-cookie");
		request.setContentType("application/json");
		request.setContent("{\"refreshToken\":\"secret-body\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> {
			req.getInputStream().readAllBytes();
			res.setContentType("application/json");
			((jakarta.servlet.http.HttpServletResponse) res).addHeader("Set-Cookie", "refreshToken=secret-response-cookie");
			res.getWriter().write("{\"accessToken\":\"secret-response-body\"}");
		});
		ArgumentCaptor<ApiRequestLog> captured = ArgumentCaptor.forClass(ApiRequestLog.class);
		verify(logs).logApi(captured.capture());
		ApiRequestLog log = captured.getValue();
		String stored = log.getRequestHeaders() + log.getResponseHeaders() + log.getQueryString() + log.getRequestBody() + log.getResponseBody();
		assertFalse(stored.contains("secret-"));
		assertNull(log.getRequestBody());
		assertNull(log.getResponseBody());
		assertEquals("[redacted]", log.getQueryString());
	}
}
