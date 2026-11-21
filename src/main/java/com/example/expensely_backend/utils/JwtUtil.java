package com.example.expensely_backend.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtUtil {
	public static final long MOBILE_ACCESS_SECONDS = 15 * 60;
	public static final long MOBILE_REFRESH_SECONDS = 7 * 24 * 60 * 60;
	public record MobileTokens(String accessToken, String refreshToken, Instant accessTokenExpiresAt,
	                           Instant refreshTokenExpiresAt, String sessionId) {}

	public final Key key;  //= Keys.secretKeyFor(SignatureAlgorithm.HS256);

	public JwtUtil(@Value("${jwt.secret}") String secret) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes());
	}

	public Map<String, String> GenerateToken(String string) {
		String accessToken =
				Jwts.builder().setSubject(string)
						.claim("token_type", "web_access")
						.setIssuedAt(new Date(System.currentTimeMillis()))
						.setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 15))
						.signWith(key)
						.compact();
		String refreshToken = Jwts.builder()
				.setSubject(string)
				.claim("token_type", "web_refresh")
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7))
				.signWith(key)
				.compact();

		Map<String, String> result = new HashMap<>();
		result.put("accessToken", accessToken);
		result.put("refreshToken", refreshToken);
		return result;
	}

	public MobileTokens issueMobileTokens(String userId, String sessionId) {
		Instant now = Instant.now();
		Instant accessExpiry = now.plusSeconds(MOBILE_ACCESS_SECONDS);
		Instant refreshExpiry = now.plusSeconds(MOBILE_REFRESH_SECONDS);
		String access = Jwts.builder().setSubject(userId).setIssuedAt(Date.from(now))
				.setExpiration(Date.from(accessExpiry)).setId(UUID.randomUUID().toString())
				.claim("token_type", "mobile_access").claim("sid", sessionId).signWith(key).compact();
		String refresh = Jwts.builder().setSubject(userId).setIssuedAt(Date.from(now))
				.setExpiration(Date.from(refreshExpiry)).setId(UUID.randomUUID().toString())
				.claim("token_type", "mobile_refresh").claim("sid", sessionId).signWith(key).compact();
		return new MobileTokens(access, refresh, accessExpiry, refreshExpiry, sessionId);
	}

	public Claims parseMobileToken(String token, String expectedType) {
		try {
			Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
			if (!expectedType.equals(claims.get("token_type", String.class)) ||
					claims.getSubject() == null || claims.get("sid", String.class) == null || claims.getId() == null) return null;
			return claims;
		} catch (RuntimeException ex) { return null; }
	}

	public String getWebAccessSubject(String token) {
		try {
			Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
			String type = claims.get("token_type", String.class);
			return type == null || "web_access".equals(type) ? claims.getSubject() : null;
		} catch (RuntimeException ex) { return null; }
	}

	public String getWebRefreshSubject(String token) {
		try {
			Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
			String type = claims.get("token_type", String.class);
			return type == null || "web_refresh".equals(type) ? claims.getSubject() : null;
		} catch (RuntimeException ex) { return null; }
	}

	public String GetStringFromToken(String token) {
		try {
			return Jwts.parserBuilder()
					.setSigningKey(key)
					.build()
					.parseClaimsJws(token)
					.getBody()
					.getSubject();

		} catch (ExpiredJwtException e) {
			return null;
		} catch (JwtException e) {
			return null;
		}
	}

	public boolean ValidateToken(String token) {
		try {
			Jwts.parserBuilder()
					.setSigningKey(key)
					.build()
					.parseClaimsJws(token);
			return true;
		} catch (ExpiredJwtException e) {
			return false;
		} catch (Exception e) {
			return false;
		}
	}

}
