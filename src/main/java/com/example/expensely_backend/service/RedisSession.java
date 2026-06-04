package com.example.expensely_backend.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;

@Service
public class RedisSession {
	private static final int SESSION_TTL_SECONDS = 60 * 60 * 24 * 7; // 7 days
	private static final String SESSION_PREFIX = "session:";
	private static final String USER_SESSIONS_PREFIX = "user:sessions:";

	private final JedisPooled redis;

	public RedisSession(
			@Value("${redis.host:oriole-alarm-outshining-32200.db.redis.io}") String host,
			@Value("${redis.port:17774}") int port,
			@Value("${redis.username:}") String username,
			@Value("${redis.password:}") String password,
			@Value("${redis.ssl:false}") boolean ssl) {
		DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder().ssl(ssl);
		if (username != null && !username.isBlank()) {
			configBuilder.user(username);
		}
		if (password != null && !password.isBlank()) {
			configBuilder.password(password);
		}
		this.redis = new JedisPooled(new HostAndPort(host, port), configBuilder.build());
	}

	@PreDestroy
	public void shutdown() {
		redis.close();
	}

	private String sessionKey(String sessionId) {
		return SESSION_PREFIX + sessionId;
	}

	private String userSessionsKey(String userId) {
		return USER_SESSIONS_PREFIX + userId;
	}


	public String createSession(String userId, String deviceId,
	                            String refreshToken, String ipAddress) {

		try {
			if (refreshToken == null || refreshToken.isBlank()) {
				return null;
			}
			String sessionKey = sessionKey(refreshToken);
			String normalizedDeviceId =
					deviceId == null || deviceId.isBlank() ? "unknown" : deviceId;
			Map<String, String> fields = new HashMap<>();
			fields.put("userId", userId);
			fields.put("deviceId", normalizedDeviceId);
			fields.put("lastSeen", String.valueOf(System.currentTimeMillis()));
			fields.put("ipAddress", ipAddress);
			redis.hset(sessionKey, fields);
			redis.expire(sessionKey, SESSION_TTL_SECONDS);
			redis.sadd(userSessionsKey(userId), refreshToken);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to create session");
			return null;
		}
		return refreshToken;

	}

	public void revokeSession(String userId, String sessionId) {
		try {
			if (sessionId == null || sessionId.isBlank()) {
				return;
			}
			redis.del(sessionKey(sessionId));
			if (userId != null && !userId.isBlank()) {
				redis.srem(userSessionsKey(userId), sessionId);
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to revoke session");
			throw new RuntimeException("Failed to revoke session");
		}
	}

	public boolean isSessionActive(String sessionId) {
		try {
			if (sessionId == null || sessionId.isBlank()) {
				return false;
			}
			return redis.exists(sessionKey(sessionId));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to check session status");
			return false;
		}
	}

	public void revokeAllSessions(String userId) {
		try {
			if (userId == null || userId.isBlank()) {
				return;
			}
			String setKey = userSessionsKey(userId);
			Set<String> sessions = redis.smembers(setKey);
			for (String session : sessions) {
				redis.del(sessionKey(session));
			}
			redis.del(setKey);

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to revoke all sessions");
			throw new RuntimeException("Failed to revoke all sessions");
		}
	}

	public void updateLastSeen(String sessionId) {
		try {
			if (sessionId == null || sessionId.isBlank()) {
				return;
			}
			String sessionKey = sessionKey(sessionId);
			if (!redis.exists(sessionKey)) {
				return;
			}
			redis.hset(sessionKey, "lastSeen", String.valueOf(System.currentTimeMillis()));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to update last seen");
			throw new RuntimeException("Failed to update last seen");
		}
	}

	public Map<String, Map<String, String>> fetchAllSessionsForUser(String userId, String myRefreshToken) {
		try {
			if (userId == null || userId.isBlank()) {
				return null;
			}
			String setKey = userSessionsKey(userId);
			Set<String> sessions = redis.smembers(setKey);
			Map<String, Map<String, String>> result = new HashMap<>();
			for (String session : sessions) {
				Map<String, String> response = redis.hgetAll(sessionKey(session));
				if (response != null) {
					if (session.equals(myRefreshToken)) {
						response.put("current", "true");
					} else {
						response.put("current", "false");
					}
					result.put(session, response);
				}
			}
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to fetch all sessions");
			throw new RuntimeException(e);
		}
	}

	public List<String> fetchAllActiveUsers() {
		List<String> activeUsers = new ArrayList<>();
		try {
			String cursor = "0";
			do {
				ScanResult<String> result = redis.scan(cursor,
						new ScanParams().match(USER_SESSIONS_PREFIX + "*").count(100));
				cursor = result.getCursor();
				for (String key : result.getResult()) {
					// key is "user:sessions:{userId}", strip the prefix to get userId
					String userId = key.substring(USER_SESSIONS_PREFIX.length());
					activeUsers.add(userId);
				}
			} while (!cursor.equals("0"));

			return activeUsers;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to fetch all active users", e);
		}
	}
}
