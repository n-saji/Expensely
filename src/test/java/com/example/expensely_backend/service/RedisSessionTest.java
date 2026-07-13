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
		assertTrue(result.containsKey("token1"));
		assertEquals("true", result.get("token1").get("current"));
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
}
