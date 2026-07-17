package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.AuthResponse;
import com.example.expensely_backend.dto.UserRes;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.RedisSession;
import com.example.expensely_backend.service.UserService;
import com.example.expensely_backend.utils.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admins")
public class AdminController {
	private final UserService userService;
	private final RedisSession redisSession;
	private final JwtUtil jwtUtil;

	public AdminController(UserService userService, RedisSession redisSession, JwtUtil jwtUtil) {
		this.userService = userService;
		this.redisSession = redisSession;
		this.jwtUtil = jwtUtil;
	}

	private User requireAdmin(Authentication authentication) {
		if (authentication == null || authentication.getPrincipal() == null) {
			return null;
		}
		String userId = (String) authentication.getPrincipal();
		User user = userService.GetUserById(userId);
		if (user.getIsAdmin() == null || !user.getIsAdmin()) {
			return null;
		}
		if (user.getIsActive() == null || !user.getIsActive()) {
			return null;
		}
		return user;
	}

	@PatchMapping("/users/{id}/activate")
	public ResponseEntity<?> activateUser(Authentication authentication, @PathVariable String id) {
		User adminUser = requireAdmin(authentication);
		if (adminUser == null) {
			return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
		}
		try {
			User user = userService.GetUserById(id);
			user.setIsActive(true);
			userService.UpdateUser(user);
			return ResponseEntity.ok(new UserRes(user, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@PatchMapping("/users/{id}/deactivate")
	public ResponseEntity<?> deactivateUser(Authentication authentication, @PathVariable String id) {
		User adminUser = requireAdmin(authentication);
		if (adminUser == null) {
			return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
		}
		try {
			User user = userService.GetUserById(id);
			user.setIsActive(false);
			userService.UpdateUser(user);
			return ResponseEntity.ok(new UserRes(user, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@PatchMapping("/users/{id}/set-admin")
	public ResponseEntity<?> setUserAsAdmin(Authentication authentication, @PathVariable String id) {
		User adminUser = requireAdmin(authentication);
		if (adminUser == null) {
			return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
		}
		try {
			User user = userService.GetUserById(id);
			user.setIsAdmin(true);
			userService.UpdateUser(user);
			return ResponseEntity.ok(new UserRes(user, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@DeleteMapping("/{id}/sessions")
	public ResponseEntity<?> revokeAllSessionsForUser(@PathVariable String id, Authentication authentication) {
		User adminUser = requireAdmin(authentication);
		if (adminUser == null) {
			return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
		}

		try {
			redisSession.revokeAllSessions(id);
			return ResponseEntity.ok(new AuthResponse("All sessions revoked", id, ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error revoking sessions", null, e.getMessage()));
		}
	}

	@GetMapping("/find-all-active")
	public ResponseEntity<?> findAllActiveSessions(Authentication authentication) {
		User adminUser = requireAdmin(authentication);
		if (adminUser == null) {
			return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
		}
		try {
			return ResponseEntity.ok(redisSession.fetchAllActiveUsers());
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error revoking sessions", null, e.getMessage()));
		}
	}

	@PostMapping("/switch-session/{targetUserId}")
	public ResponseEntity<?> switchSession(
			Authentication authentication,
			HttpServletRequest request,
			@PathVariable String targetUserId) {
		User adminUser = requireAdmin(authentication);
		if (adminUser == null) {
			return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
		}

		try {
			User targetUser = userService.GetUserById(targetUserId);
			if (targetUser == null) {
				return ResponseEntity.status(404).body(new UserRes(null, "Target user not found"));
			}

			if (targetUser.getIsActive() == null || !targetUser.getIsActive()) {
				return ResponseEntity.badRequest().body(new UserRes(null, "Cannot switch session to a deactivated user"));
			}

			// Generate new tokens for the target user
			Map<String, String> tokens = jwtUtil.GenerateToken(targetUser.getId().toString());
			String accessToken = tokens.get("accessToken");
			String refreshToken = tokens.get("refreshToken");

			String myIP = "";
			if (request.getHeader("X-Forwarded-For") != null) {
				myIP = request.getHeader("X-Forwarded-For").split(",")[0];
			} else {
				myIP = request.getRemoteAddr();
			}

			// Create Redis session for target user
			redisSession.createSession(targetUser.getId().toString(),
					request.getHeader("User-Agent"), refreshToken, myIP);

			// Cookies for target user
			ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
					.httpOnly(true)
					.secure(true)
					.path("/")
					.sameSite("None")
					.maxAge(15 * 60)  // 15 mins
					.build();

			ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
					.httpOnly(true)
					.secure(true)
					.path("/")
					.sameSite("None")
					.maxAge(7 * 24 * 60 * 60) // 7 days
					.build();

			// Store admin's original refresh token in a cookie so we can switch back
			String originalAdminRefreshToken = null;
			if (request.getCookies() != null) {
				for (Cookie cookie : request.getCookies()) {
					if (cookie.getName().equals("refreshToken")) {
						originalAdminRefreshToken = cookie.getValue();
						break;
					}
				}
			}

			HttpHeaders headers = new HttpHeaders();
			headers.add(HttpHeaders.SET_COOKIE, accessCookie.toString());
			headers.add(HttpHeaders.SET_COOKIE, refreshCookie.toString());

			if (originalAdminRefreshToken != null) {
				ResponseCookie adminRefreshCookie = ResponseCookie.from("adminRefreshToken", originalAdminRefreshToken)
						.httpOnly(true)
						.secure(true)
						.path("/")
						.sameSite("None")
						.maxAge(7 * 24 * 60 * 60) // 7 days
						.build();
				headers.add(HttpHeaders.SET_COOKIE, adminRefreshCookie.toString());
			}

			return ResponseEntity.ok().headers(headers).body(new UserRes(targetUser, null, true));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}

	@PostMapping("/cancel-switch-session")
	public ResponseEntity<?> cancelSwitchSession(HttpServletRequest request) {
		String adminRefreshToken = null;
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (cookie.getName().equals("adminRefreshToken")) {
					adminRefreshToken = cookie.getValue();
					break;
				}
			}
		}

		if (adminRefreshToken == null) {
			return ResponseEntity.badRequest().body(new UserRes(null, "No switch session active"));
		}

		String adminId = jwtUtil.GetStringFromToken(adminRefreshToken);
		if (adminId == null) {
			return ResponseEntity.status(401).body(new UserRes(null, "Invalid admin session"));
		}

		if (!redisSession.isSessionActive(adminRefreshToken)) {
			return ResponseEntity.status(401).body(new UserRes(null, "Admin session expired or revoked"));
		}

		User adminUser = userService.GetUserById(adminId);
		if (adminUser == null || adminUser.getIsAdmin() == null || !adminUser.getIsAdmin() || adminUser.getIsActive() == null || !adminUser.getIsActive()) {
			return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
		}

		try {
			// Generate new access token for admin
			Map<String, String> tokens = jwtUtil.GenerateToken(adminUser.getId().toString());
			String accessToken = tokens.get("accessToken");

			ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
					.httpOnly(true)
					.secure(true)
					.path("/")
					.sameSite("None")
					.maxAge(15 * 60)  // 15 mins
					.build();

			ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", adminRefreshToken)
					.httpOnly(true)
					.secure(true)
					.path("/")
					.sameSite("None")
					.maxAge(7 * 24 * 60 * 60) // 7 days
					.build();

			ResponseCookie clearAdminRefreshCookie = ResponseCookie.from("adminRefreshToken", "")
					.httpOnly(true)
					.secure(true)
					.path("/")
					.maxAge(0)
					.sameSite("None")
					.build();

			redisSession.updateLastSeen(adminRefreshToken);

			HttpHeaders headers = new HttpHeaders();
			headers.add(HttpHeaders.SET_COOKIE, accessCookie.toString());
			headers.add(HttpHeaders.SET_COOKIE, refreshCookie.toString());
			headers.add(HttpHeaders.SET_COOKIE, clearAdminRefreshCookie.toString());

			return ResponseEntity.ok().headers(headers).body(new UserRes(adminUser, null, false));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, e.getMessage()));
		}
	}
}

