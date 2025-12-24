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
        String refreshToken = null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals("refreshToken")) {
                refreshToken = cookie.getValue();
            }
        }
        if (refreshToken == null) {
            return "";
        }

        return jwtUtil.GetStringFromToken(refreshToken);
    }
}
