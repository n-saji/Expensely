package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.handler.AlertHandler;
import com.example.expensely_backend.model.FunctionLog;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class RedisSession {
	private static final int SESSION_TTL_SECONDS = 60 * 60 * 24 * 7; // 7 days
	private static final String SESSION_PREFIX = "session:";
	private static final String USER_SESSIONS_PREFIX = "user:sessions:";
	private static final String MOBILE_SESSION_PREFIX = "mobile:session:";
	private static final String MOBILE_USER_SESSIONS_PREFIX = "mobile:user:sessions:";
	private static final String ROTATE_MOBILE_REFRESH = "if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1] then return 0 end " +
			"if redis.call('HGET', KEYS[1], 'refreshHash') ~= ARGV[2] then " +
			"redis.call('DEL', KEYS[1]) redis.call('SREM', KEYS[2], ARGV[6]) return -1 end " +
			"redis.call('HSET', KEYS[1], 'refreshHash', ARGV[3], 'lastSeen', ARGV[4]) " +
			"redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5])) " +
			"redis.call('EXPIRE', KEYS[2], tonumber(ARGV[5])) return 1";

	private final JedisPooled redis;
	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ObjectProvider<AlertHandler> alertHandler;

	@Autowired
	private DbLogService dbLogService;

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

	private String mobileSessionKey(String sessionId) { return MOBILE_SESSION_PREFIX + sessionId; }
	private String mobileUserSessionsKey(String userId) { return MOBILE_USER_SESSIONS_PREFIX + userId; }

	private static String tokenHash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
	}

	public void createMobileSession(String userId, String sessionId, String refreshToken, String device, String ipAddress) {
		Map<String, String> fields = new HashMap<>();
		fields.put("userId", userId);
		fields.put("refreshHash", tokenHash(refreshToken));
		fields.put("deviceId", device == null || device.isBlank() ? "unknown" : device);
		fields.put("ipAddress", ipAddress == null ? "" : ipAddress);
		fields.put("lastSeen", String.valueOf(System.currentTimeMillis()));
		String key = mobileSessionKey(sessionId);
		redis.hset(key, fields);
		redis.expire(key, SESSION_TTL_SECONDS);
		redis.sadd(mobileUserSessionsKey(userId), sessionId);
		redis.expire(mobileUserSessionsKey(userId), SESSION_TTL_SECONDS);
	}

	public boolean isMobileSessionActive(String userId, String sessionId) {
		try { return userId.equals(redis.hget(mobileSessionKey(sessionId), "userId")); }
		catch (RuntimeException e) { return false; }
	}

	public boolean isMobileRefreshActive(String userId, String sessionId, String refreshToken) {
		if (refreshToken == null || !isMobileSessionActive(userId, sessionId)) return false;
		return tokenHash(refreshToken).equals(redis.hget(mobileSessionKey(sessionId), "refreshHash"));
	}

	public boolean rotateMobileRefresh(String userId, String sessionId, String oldToken, String nextToken) {
		try {
			Object result = redis.eval(ROTATE_MOBILE_REFRESH, List.of(mobileSessionKey(sessionId), mobileUserSessionsKey(userId)),
					List.of(userId, tokenHash(oldToken), tokenHash(nextToken), String.valueOf(System.currentTimeMillis()), String.valueOf(SESSION_TTL_SECONDS), sessionId));
			return result instanceof Number && ((Number) result).longValue() == 1L;
		} catch (RuntimeException e) { return false; }
	}

	public void revokeMobileSession(String userId, String sessionId) {
		if (!isMobileSessionActive(userId, sessionId)) return;
		redis.del(mobileSessionKey(sessionId));
		redis.srem(mobileUserSessionsKey(userId), sessionId);
		AlertHandler handler = alertHandler.getIfAvailable();
		if (handler != null) handler.sendSessionLogOut(UUID.fromString(userId), "mobile:" + sessionId,
				MessageDTO.builder().message("Session revoked").type(globals.MessageType.LOGOUT).build());
	}

	public Map<String, Map<String, String>> fetchMobileSessionsForUser(String userId, String currentSessionId) {
		Map<String, Map<String, String>> result = new HashMap<>();
		String setKey = mobileUserSessionsKey(userId);
		for (String sessionId : redis.smembers(setKey)) {
			Map<String, String> fields = redis.hgetAll(mobileSessionKey(sessionId));
			if (fields == null || fields.isEmpty() || !userId.equals(fields.get("userId"))) {
				redis.srem(setKey, sessionId);
				continue;
			}
			Map<String, String> visible = new HashMap<>(fields);
			visible.remove("refreshHash");
			visible.put("current", String.valueOf(sessionId.equals(currentSessionId)));
			visible.put("platform", "mobile");
			result.put(sessionId, visible);
		}
		return result;
	}

	public void revokeOtherMobileSessions(String userId, String currentSessionId) {
		for (String sessionId : redis.smembers(mobileUserSessionsKey(userId))) {
			if (!sessionId.equals(currentSessionId)) revokeMobileSession(userId, sessionId);
		}
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
			System.out.println("Failed to create session");
			dbLogService.logError("redis-service", getClass().getName(),
					"createSession",
					"Failed to create session: " + e.getMessage(), e);
			return null;
		}
		return refreshToken;

	}

	public void revokeSession(String userId, String sessionId) {
		try {
			if (sessionId == null || sessionId.isBlank() || userId == null) {
				return;
			}
			if (!userId.equals(redis.hget(sessionKey(sessionId), "userId"))) return;
			redis.del(sessionKey(sessionId));
			if (!userId.isBlank()) {
				redis.srem(userSessionsKey(userId), sessionId);
			}
			AlertHandler handler = alertHandler.getIfAvailable();
			if (handler != null) handler.sendSessionLogOut(UUID.fromString(userId), sessionId,
					MessageDTO.builder().message("User: " + userId + " is booted").type(globals.MessageType.LOGOUT).build());

		} catch (Exception e) {
			dbLogService.logError("redis-service", getClass().getName(), "revokeSession",
					"Failed to revoke session: " + e.getMessage(), e);
			System.out.println("Failed to revoke session");
			throw new RuntimeException("Failed to revoke session");
		}
	}

	public void revokeSessionByHandle(String userId, String handle) {
		if (userId == null || handle == null || handle.isBlank()) return;
		for (String token : redis.smembers(userSessionsKey(userId))) {
			if (tokenHash(token).equals(handle)) {
				revokeSession(userId, token);
				return;
			}
		}
	}

	public boolean isSessionActive(String sessionId) {
		try {
			if (sessionId == null || sessionId.isBlank()) {
				return false;
			}
			return redis.exists(sessionKey(sessionId));
		} catch (Exception e) {
			dbLogService.logError("redis-service", getClass().getName(), "isSessionActive",
					"Failed to check session status: " + e.getMessage(), e);
			System.out.println("Failed to check session status");
			return false;
		}
	}

	public boolean isWebSessionActive(String userId, String refreshToken) {
		if (userId == null || refreshToken == null || refreshToken.isBlank()) return false;
		try { return userId.equals(redis.hget(sessionKey(refreshToken), "userId")); }
		catch (RuntimeException e) { return false; }
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
			String mobileSetKey = mobileUserSessionsKey(userId);
			for (String sessionId : redis.smembers(mobileSetKey)) redis.del(mobileSessionKey(sessionId));
			redis.del(mobileSetKey);

		} catch (Exception e) {
			dbLogService.logError("redis-service", getClass().getName(), "revokeAllSessions",
					"Failed to revoke all sessions with error: " + e.getMessage(), e);
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
			dbLogService.logError("redis-service", getClass().getName(), "updateLastSeen",
					"Failed to update last seen with error: " + e.getMessage(), e);
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
				if (response == null || response.isEmpty()) {
					redis.srem(setKey, session);
				} else {
					Map<String, String> responseCopy = new HashMap<>(response);
					if (session.equals(myRefreshToken)) {
						responseCopy.put("current", "true");
					} else {
						responseCopy.put("current", "false");
					}
					result.put(tokenHash(session), responseCopy);
				}
			}
			return result;
		} catch (Exception e) {
			dbLogService.logError("redis-service", getClass().getName(), "fetchAllSessionsForUser",
					"Failed to fetch all sessions with error: " + e.getMessage(), e);
			System.out.println("Failed to fetch all sessions");
			throw new RuntimeException(e);
		}
	}

	public List<User> fetchAllActiveUsers() {
		List<User> activeUsers = new ArrayList<>();
		try {
			String cursor = "0";
			do {
				ScanResult<String> result = redis.scan(cursor,
						new ScanParams().match(USER_SESSIONS_PREFIX + "*").count(100));
				cursor = result.getCursor();
				for (String key : result.getResult()) {
					// key is "user:sessions:{userId}", strip the prefix to get userId
					String userId = key.substring(USER_SESSIONS_PREFIX.length());
					Set<String> sessions = redis.smembers(key);
					boolean hasActiveSession = false;
					for (String session : sessions) {
						if (!redis.exists(sessionKey(session))) {
							redis.srem(key, session);
						} else {
							hasActiveSession = true;
						}
					}
					if (hasActiveSession) {
						userRepository.findById(UUID.fromString(userId)).ifPresent(u -> {
							u.setPassword(null);
							activeUsers.add(u);
						});
					}
				}
			} while (!cursor.equals("0"));

			return activeUsers;
		} catch (Exception e) {
			dbLogService.logError("redis-service", getClass().getName(), "fetchAllActiveUsers",
					"Failed to fetch all active users with error: " + e.getMessage(), e);
			throw new RuntimeException("Failed to fetch all active users", e);
		}
	}
}
