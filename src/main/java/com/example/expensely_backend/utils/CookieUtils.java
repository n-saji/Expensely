package com.example.expensely_backend.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CookieUtils {

	private final JwtUtil jwtUtil;

	public CookieUtils(JwtUtil jwtUtil) {
		this.jwtUtil = jwtUtil;
	}


	public String getStringFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		String accessToken = null;
		for (Cookie cookie : cookies) {
			if (cookie.getName().equals("accessToken")) {
				accessToken = cookie.getValue();
			}
		}
		if (accessToken == null) {
			return "";
		}

		return jwtUtil.GetStringFromToken(accessToken);
	}
}
