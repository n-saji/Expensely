package com.example.expensely_backend.service;

import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisPooled;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisSessionTest {

	private RedisSession redisSession;
	private JedisPooled mockRedis;
	private UserRepository mockUserRepository;

	@BeforeEach
	void setUp() {
		mockRedis = mock(JedisPooled.class);
		mockUserRepository = mock(UserRepository.class);

		redisSession = new RedisSession("localhost", 6379, "", "", false);
		ReflectionTestUtils.setField(redisSession, "redis", mockRedis);
		ReflectionTestUtils.setField(redisSession, "userRepository", mockUserRepository);
		ReflectionTestUtils.setField(redisSession, "alertHandler", mock(org.springframework.beans.factory.ObjectProvider.class));
		ReflectionTestUtils.setField(redisSession, "dbLogService", mock(DbLogService.class));
	}

	@Test
	void testFetchAllSessionsForUserCleansUpExpired() {
		String userId = "user123";
		String myToken = "token1";
		String setKey = "user:sessions:" + userId;

		Set<String> sessions = new HashSet<>(Arrays.asList("token1", "token2"));
		when(mockRedis.smembers(setKey)).thenReturn(sessions);

		Map<String, String> token1Fields = new HashMap<>();
		token1Fields.put("userId", userId);
		token1Fields.put("deviceId", "iPhone");
		when(mockRedis.hgetAll("session:token1")).thenReturn(token1Fields);
		when(mockRedis.hgetAll("session:token2")).thenReturn(new HashMap<>());

		Map<String, Map<String, String>> result = redisSession.fetchAllSessionsForUser(userId, myToken);

		verify(mockRedis).srem(setKey, "token2");
		verify(mockRedis, never()).srem(setKey, "token1");

		assertEquals(1, result.size());
		assertEquals(64, result.keySet().iterator().next().length());
		assertFalse(result.containsKey("token1"));
		assertEquals("true", result.values().iterator().next().get("current"));
		assertFalse(result.containsKey("token2"));
	}

	@Test
	void testFetchAllActiveUsersCleansUpExpired() {
		redis.clients.jedis.resps.ScanResult<String> scanResult = mock(redis.clients.jedis.resps.ScanResult.class);
		when(scanResult.getCursor()).thenReturn("0");
		when(scanResult.getResult()).thenReturn(Collections.singletonList("user:sessions:user123"));

		when(mockRedis.scan(eq("0"), any(redis.clients.jedis.params.ScanParams.class)))
				.thenReturn(scanResult);

		Set<String> sessions = new HashSet<>(Arrays.asList("token1", "token2"));
		when(mockRedis.smembers("user:sessions:user123")).thenReturn(sessions);

		when(mockRedis.exists("session:token1")).thenReturn(false);
		when(mockRedis.exists("session:token2")).thenReturn(false);

		List<User> activeUsers = redisSession.fetchAllActiveUsers();

		verify(mockRedis).srem("user:sessions:user123", "token1");
		verify(mockRedis).srem("user:sessions:user123", "token2");

		verifyNoInteractions(mockUserRepository);
		assertTrue(activeUsers.isEmpty());
	}

	@Test
	void mobileRefreshIsStoredAsHashAndRotationDependsOnAtomicRedisResult() {
		redisSession.createMobileSession("user", "session", "refresh-secret", "phone", "127.0.0.1");
		org.mockito.ArgumentCaptor<Map<String, String>> fields = org.mockito.ArgumentCaptor.forClass(Map.class);
		verify(mockRedis).hset(eq("mobile:session:session"), fields.capture());
		assertEquals("user", fields.getValue().get("userId"));
		assertNotEquals("refresh-secret", fields.getValue().get("refreshHash"));
		assertEquals(64, fields.getValue().get("refreshHash").length());
		when(mockRedis.eval(anyString(), anyList(), anyList())).thenReturn(1L, 0L);
		assertTrue(redisSession.rotateMobileRefresh("user", "session", "refresh-secret", "next-secret"));
		assertFalse(redisSession.rotateMobileRefresh("user", "session", "refresh-secret", "another-secret"));
	}

	@Test
	void mobileRefreshRequiresCurrentTokenHash() throws Exception {
		when(mockRedis.hget("mobile:session:session", "userId")).thenReturn("user");
		String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
				.digest("current-token".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		when(mockRedis.hget("mobile:session:session", "refreshHash")).thenReturn(hash);
		assertTrue(redisSession.isMobileRefreshActive("user", "session", "current-token"));
		assertFalse(redisSession.isMobileRefreshActive("user", "session", "rotated-token"));
		assertFalse(redisSession.isMobileRefreshActive("other-user", "session", "current-token"));
	}

	@Test
	void mobileSessionListingHidesRefreshHashAndMarksCurrentSession() {
		when(mockRedis.smembers("mobile:user:sessions:user")).thenReturn(Set.of("current", "expired"));
		when(mockRedis.hgetAll("mobile:session:current")).thenReturn(Map.of("userId", "user", "deviceId", "iPhone", "refreshHash", "secret-hash"));
		when(mockRedis.hgetAll("mobile:session:expired")).thenReturn(Map.of());
		Map<String, Map<String, String>> result = redisSession.fetchMobileSessionsForUser("user", "current");
		assertEquals(1, result.size());
		assertEquals("true", result.get("current").get("current"));
		assertEquals("mobile", result.get("current").get("platform"));
		assertFalse(result.get("current").containsKey("refreshHash"));
		verify(mockRedis).srem("mobile:user:sessions:user", "expired");
	}

	@Test
	void opaqueWebSessionHandleRevokesOnlyOwnedSession() throws Exception {
		String userId = "user123";
		String token = "web-refresh-secret";
		when(mockRedis.smembers("user:sessions:" + userId)).thenReturn(Set.of(token));
		when(mockRedis.hget("session:" + token, "userId")).thenReturn(userId);
		String handle = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		redisSession.revokeSessionByHandle(userId, handle);
		verify(mockRedis).del("session:" + token);
		verify(mockRedis).srem("user:sessions:" + userId, token);
	}
}
