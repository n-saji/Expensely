package com.example.expensely_backend.controller;

import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.UserRepository;
import com.example.expensely_backend.service.RedisSession;
import com.example.expensely_backend.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {
	public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {}
	public record RefreshRequest(@NotBlank String refreshToken) {}
	public record SessionResponse(String accessToken, String refreshToken, Instant accessTokenExpiresAt,
	                              Instant refreshTokenExpiresAt, String sessionId) {
		static SessionResponse from(JwtUtil.MobileTokens tokens) {
			return new SessionResponse(tokens.accessToken(), tokens.refreshToken(), tokens.accessTokenExpiresAt(),
					tokens.refreshTokenExpiresAt(), tokens.sessionId());
		}
	}

	private final UserRepository users;
	private final PasswordEncoder passwords;
	private final JwtUtil jwt;
	private final RedisSession sessions;

	public MobileAuthController(UserRepository users, PasswordEncoder passwords, JwtUtil jwt, RedisSession sessions) {
		this.users = users;
		this.passwords = passwords;
		this.jwt = jwt;
		this.sessions = sessions;
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		String identifier = request.identifier().trim();
		User user = (identifier.contains("@") ? users.findByEmail(identifier) : users.findByPhone(identifier)).orElse(null);
		if (user == null || user.getPassword() == null || !passwords.matches(request.password(), user.getPassword()))
			return ResponseEntity.status(401).body(Map.of("code", "INVALID_CREDENTIALS", "message", "Invalid credentials"));
		if (!Boolean.TRUE.equals(user.getIsActive()))
			return ResponseEntity.status(403).body(Map.of("code", "ACCOUNT_INACTIVE", "message", "Account is inactive"));
		if (!user.isEmailVerified())
			return ResponseEntity.status(403).body(Map.of("code", "EMAIL_UNVERIFIED", "message", "Email is not verified"));
		String sessionId = UUID.randomUUID().toString();
		JwtUtil.MobileTokens tokens = jwt.issueMobileTokens(user.getId().toString(), sessionId);
		sessions.createMobileSession(user.getId().toString(), sessionId, tokens.refreshToken(),
				httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr());
		return ResponseEntity.ok(SessionResponse.from(tokens));
	}

	@PostMapping("/refresh")
	public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
		Claims claims = jwt.parseMobileToken(request.refreshToken(), "mobile_refresh");
		if (claims == null) return unauthorized();
		String userId = claims.getSubject();
		String sessionId = claims.get("sid", String.class);
		User user;
		try { user = users.findById(UUID.fromString(userId)).orElse(null); }
		catch (IllegalArgumentException e) { return unauthorized(); }
		if (user == null || !Boolean.TRUE.equals(user.getIsActive()) || !user.isEmailVerified()) {
			sessions.revokeMobileSession(userId, sessionId);
			return unauthorized();
		}
		JwtUtil.MobileTokens next = jwt.issueMobileTokens(userId, sessionId);
		if (!sessions.rotateMobileRefresh(userId, sessionId, request.refreshToken(), next.refreshToken())) return unauthorized();
		return ResponseEntity.ok(SessionResponse.from(next));
	}

	@PostMapping("/logout")
	public ResponseEntity<?> logout(Authentication authentication, @RequestHeader("Authorization") String authorization) {
		if (!authorization.startsWith("Bearer ")) return unauthorized();
		Claims claims = jwt.parseMobileToken(authorization.substring(7), "mobile_access");
		if (claims == null || authentication == null || !claims.getSubject().equals(authentication.getPrincipal())) return unauthorized();
		sessions.revokeMobileSession(claims.getSubject(), claims.get("sid", String.class));
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<Map<String, String>> unauthorized() {
		return ResponseEntity.status(401).body(Map.of("code", "INVALID_SESSION", "message", "Session expired or revoked"));
	}
}
