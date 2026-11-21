package com.example.expensely_backend.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.UserRepository;
import com.example.expensely_backend.service.RedisSession;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
	private final JwtUtil jwtUtil;
	private final ObjectProvider<RequestMappingHandlerMapping> requestMappingHandlerMappingProvider;
	private final UserRepository userRepository;
	private final RedisSession redisSession;

	public JwtAuthFilter(JwtUtil jwtUtil,
	                     ObjectProvider<RequestMappingHandlerMapping> requestMappingHandlerMappingProvider,
	                     UserRepository userRepository, RedisSession redisSession) {
		this.jwtUtil = jwtUtil;
		this.requestMappingHandlerMappingProvider = requestMappingHandlerMappingProvider;
		this.userRepository = userRepository;
		this.redisSession = redisSession;
	}


	@Override
	protected void doFilterInternal(HttpServletRequest request,
	                                HttpServletResponse response,
	                                FilterChain filterChain)
			throws ServletException, IOException {

		String path = request.getServletPath();

		if (path.startsWith("/ws/")) {
			filterChain.doFilter(request, response); // skip JWT for WebSocket handshake
			return;
		}
		if (request.getMethod().equals("OPTIONS") || path.equals("/api/mobile/auth/login") ||
				path.equals("/api/mobile/auth/refresh") || path.equals("/api/users/login") || path.equals("/api/users/register") || path.equals(
				"/ping") || path.equals("/api/users/refresh") || path.equals("/api/users/verify" +
				"-oauth-login") || path.equals("/api/users/verify-otp") || path.equals(
				"/api/users/resend-otp") || path.equals("/api/users/request-password-reset") || path.equals(
				"/api/users/confirm-password-reset") || path.equals("/api" +
				"/users/logout")) {
			filterChain.doFilter(request, response); // Skip JWT check
			return;
		}

		if (!hasHandler(request)) {
			filterChain.doFilter(request, response); // let MVC return 404 for unknown endpoints
			return;
		}

		String token = null;
		String webRefreshToken = null;
		String authorization = request.getHeader("Authorization");
		boolean bearer = authorization != null;
		if (bearer && authorization.startsWith("Bearer ")) token = authorization.substring(7);
		if (!bearer && request.getCookies() != null) {
			for (var cookie : request.getCookies()) {
				if (cookie.getName().equals("accessToken")) token = cookie.getValue();
				if (cookie.getName().equals("refreshToken")) webRefreshToken = cookie.getValue();
			}
		}
		if (token == null || token.isBlank()) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token not found");
			return;
		}
		try {
			String userSubject;
			if (bearer) {
				Claims claims = jwtUtil.parseMobileToken(token, "mobile_access");
				if (claims == null || !redisSession.isMobileSessionActive(claims.getSubject(), claims.get("sid", String.class))) {
					response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid mobile session");
					return;
				}
				userSubject = claims.getSubject();
			} else {
				userSubject = jwtUtil.getWebAccessSubject(token);
				if (userSubject == null || !userSubject.equals(jwtUtil.getWebRefreshSubject(webRefreshToken)) ||
						!redisSession.isWebSessionActive(userSubject, webRefreshToken)) {
					response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid web session");
					return;
				}
			}
			if (userSubject == null) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
				return;
			}

			User user = resolveUser(userSubject);
			if (user == null) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
				return;
			}
			if (!Boolean.TRUE.equals(user.getIsActive())) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Inactive account");
				return;
			}
			if (!user.isEmailVerified()) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "email not verified");
				return;
			}

			UsernamePasswordAuthenticationToken authentication =
					new UsernamePasswordAuthenticationToken(user.getId().toString(), null, Collections.emptyList());

			authentication.setDetails(
					new WebAuthenticationDetailsSource().buildDetails(request)
			);

			SecurityContextHolder.getContext().setAuthentication(authentication);

		} catch (Exception e) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token");
			return;
		}

		filterChain.doFilter(request, response);
	}

	private boolean hasHandler(HttpServletRequest request) {
		try {
			RequestMappingHandlerMapping mapping = requestMappingHandlerMappingProvider.getIfAvailable();
			return mapping == null || mapping.getHandler(request) != null;
		} catch (Exception e) {
			return true; // fail closed: keep auth checks if handler lookup fails
		}
	}

	private User resolveUser(String subject) {
		try {
			return userRepository.findById(java.util.UUID.fromString(subject)).orElse(null);
		} catch (IllegalArgumentException e) {
			return userRepository.findByEmail(subject).orElse(null);
		}
	}
}
