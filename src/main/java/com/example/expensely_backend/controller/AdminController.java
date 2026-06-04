package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.AuthResponse;
import com.example.expensely_backend.dto.UserRes;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.RedisSession;
import com.example.expensely_backend.service.UserService;
import com.example.expensely_backend.utils.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admins")
public class AdminController {
	private final UserService userService;
	private final RedisSession redisSession;

	public AdminController(UserService userService, RedisSession redisSession) {
		this.userService = userService;
		this.redisSession = redisSession;
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
}

