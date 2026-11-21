package com.example.expensely_backend.utils;

import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.UserRepository;
import com.example.expensely_backend.service.RedisSession;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {
	private final JwtUtil jwt = new JwtUtil("a-secret-long-enough-for-hmac-sha256-signing");
	private final UserRepository users = mock(UserRepository.class);
	private final RedisSession sessions = mock(RedisSession.class);
	private final ObjectProvider<RequestMappingHandlerMapping> mappings = mock(ObjectProvider.class);
	private final JwtAuthFilter filter = new JwtAuthFilter(jwt, mappings, users, sessions);

	@AfterEach
	void clearSecurityContext() { SecurityContextHolder.clearContext(); }

	@Test
	void bearerRequiresActiveMobileSessionAndRejectsRefreshPurpose() throws Exception {
		UUID id = UUID.randomUUID();
		JwtUtil.MobileTokens tokens = jwt.issueMobileTokens(id.toString(), "session-id");
		prepareUser(id);
		MockHttpServletRequest request = request("Bearer " + tokens.accessToken());
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> fail("Revoked session must not reach handler"));
		assertEquals(401, response.getStatus());
		when(sessions.isMobileSessionActive(id.toString(), "session-id")).thenReturn(true);
		response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> assertEquals(id.toString(), SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
		assertEquals(200, response.getStatus());
		SecurityContextHolder.clearContext();
		response = new MockHttpServletResponse();
		filter.doFilter(request("Bearer " + tokens.refreshToken()), response, (req, res) -> fail("Refresh token must not reach handler"));
		assertEquals(401, response.getStatus());
	}

	@Test
	void webCookieRequiresMatchingActiveRefreshSession() throws Exception {
		UUID id = UUID.randomUUID();
		prepareUser(id);
		var tokens = jwt.GenerateToken(id.toString());
		MockHttpServletRequest request = request(null);
		request.setCookies(new Cookie("accessToken", tokens.get("accessToken")), new Cookie("refreshToken", tokens.get("refreshToken")));
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> fail("Revoked cookie must not reach handler"));
		assertEquals(401, response.getStatus());
		when(sessions.isWebSessionActive(id.toString(), tokens.get("refreshToken"))).thenReturn(true);
		response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> assertEquals(id.toString(), SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
		assertEquals(200, response.getStatus());
	}

	private MockHttpServletRequest request(String authorization) throws Exception {
		RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
		when(mappings.getIfAvailable()).thenReturn(mapping);
		when(mapping.getHandler(any())).thenReturn(new HandlerExecutionChain(new Object()));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
		request.setServletPath("/api/users/me");
		if (authorization != null) request.addHeader("Authorization", authorization);
		return request;
	}

	private void prepareUser(UUID id) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getIsActive()).thenReturn(true);
		when(user.isEmailVerified()).thenReturn(true);
		when(users.findById(id)).thenReturn(Optional.of(user));
	}
}
