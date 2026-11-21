package com.example.expensely_backend.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilMobileTest {
	private final JwtUtil jwt = new JwtUtil("a-secret-long-enough-for-hmac-sha256-signing");

	@Test
	void mobileTokensHaveDistinctPurposesAndSessionIdentity() {
		JwtUtil.MobileTokens tokens = jwt.issueMobileTokens("user-id", "session-id");
		assertEquals("user-id", jwt.parseMobileToken(tokens.accessToken(), "mobile_access").getSubject());
		assertEquals("session-id", jwt.parseMobileToken(tokens.refreshToken(), "mobile_refresh").get("sid"));
		assertNull(jwt.parseMobileToken(tokens.refreshToken(), "mobile_access"));
		assertNull(jwt.parseMobileToken(tokens.accessToken(), "mobile_refresh"));
		assertNull(jwt.getWebAccessSubject(tokens.accessToken()));
		assertNotEquals(tokens.accessToken(), tokens.refreshToken());
		assertTrue(tokens.refreshTokenExpiresAt().isAfter(tokens.accessTokenExpiresAt()));
	}

	@Test
	void oldWebTokenStillWorksOnlyAsWebToken() {
		String webAccess = jwt.GenerateToken("web-user").get("accessToken");
		assertEquals("web-user", jwt.getWebAccessSubject(webAccess));
		assertNull(jwt.parseMobileToken(webAccess, "mobile_access"));
		assertNull(jwt.getWebRefreshSubject(webAccess));
		assertEquals("web-user", jwt.getWebRefreshSubject(jwt.GenerateToken("web-user").get("refreshToken")));
	}
}
