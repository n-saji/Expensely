package com.example.expensely_backend.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        if (path.equals("/api/users/login") || path.equals("/api/users/register") || path.equals(
                "/ping") || path.equals("/api/users/refresh") || path.equals("/api/users/verify" +
                "-oauth-login")) {
            filterChain.doFilter(request, response); // Skip JWT check
            return;
        }
        String token = null;

        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (cookie.getName().equals("accessToken")) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        System.out.println("token:" + token);
        if (token == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token not found");
//            filterChain.doFilter(request, response);
            return;
        }
        System.out.println("hello");
        try {
            String email = jwtUtil.GetStringFromToken(token);
            if (email == null) {
                throw new BadCredentialsException("Token expired or invalid");
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token");
            System.out.println(e.getMessage() + " invalid token");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
