package com.example.expensely_backend.controller;

import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.UserRepository;
import com.example.expensely_backend.service.RedisSession;
import com.example.expensely_backend.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobileAuthControllerTest {
	private final UserRepository users = mock(UserRepository.class);
	private final PasswordEncoder passwords = mock(PasswordEncoder.class);
	private final RedisSession sessions = mock(RedisSession.class);
	private final JwtUtil jwt = new JwtUtil("a-secret-long-enough-for-hmac-sha256-signing");
	private final MobileAuthController controller = new MobileAuthController(users, passwords, jwt, sessions);

	@Test
	void loginIssuesSessionAndRefreshRotatesOnlyCurrentCredential() {
		UUID userId = UUID.randomUUID();
		User user = mock(User.class);
		when(user.getId()).thenReturn(userId);
		when(user.getPassword()).thenReturn("hash");
		when(user.getIsActive()).thenReturn(true);
		when(user.isEmailVerified()).thenReturn(true);
		when(users.findByEmail("person@example.com")).thenReturn(Optional.of(user));
		when(users.findById(userId)).thenReturn(Optional.of(user));
		when(passwords.matches("password", "hash")).thenReturn(true);
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("User-Agent")).thenReturn("test-device");
		when(request.getRemoteAddr()).thenReturn("127.0.0.1");
		MobileAuthController.SessionResponse first = (MobileAuthController.SessionResponse) controller
				.login(new MobileAuthController.LoginRequest("person@example.com", "password"), request).getBody();
		assertNotNull(first);
		verify(sessions).createMobileSession(eq(userId.toString()), eq(first.sessionId()), eq(first.refreshToken()), eq("test-device"), eq("127.0.0.1"));
		when(sessions.rotateMobileRefresh(eq(userId.toString()), eq(first.sessionId()), eq(first.refreshToken()), anyString())).thenReturn(true, false);
		MobileAuthController.SessionResponse second = (MobileAuthController.SessionResponse) controller
				.refresh(new MobileAuthController.RefreshRequest(first.refreshToken())).getBody();
		assertNotNull(second);
		assertEquals(first.sessionId(), second.sessionId());
		assertNotEquals(first.refreshToken(), second.refreshToken());
		assertEquals(401, controller.refresh(new MobileAuthController.RefreshRequest(first.refreshToken())).getStatusCode().value());
		ResponseEntity<?> logout = controller.logout(new UsernamePasswordAuthenticationToken(userId.toString(), null), "Bearer " + second.accessToken());
		assertEquals(204, logout.getStatusCode().value());
		verify(sessions).revokeMobileSession(userId.toString(), first.sessionId());
	}

	@Test
	void refreshRejectsAccessTokenAndLoginRejectsBadPassword() {
		JwtUtil.MobileTokens tokens = jwt.issueMobileTokens(UUID.randomUUID().toString(), "session");
		assertEquals(401, controller.refresh(new MobileAuthController.RefreshRequest(tokens.accessToken())).getStatusCode().value());
		assertEquals(401, controller.login(new MobileAuthController.LoginRequest("missing@example.com", "wrong"), mock(HttpServletRequest.class)).getStatusCode().value());
		verifyNoInteractions(sessions);
	}
}
