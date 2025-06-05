package com.example.expensely_backend.utils;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    public final Key key ;  //= Keys.secretKeyFor(SignatureAlgorithm.HS256);

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String GenerateToken(String string) {
        long expirationTime = 1000 * 60 * 60 * 24; // 1 day in milliseconds
        return Jwts.builder()
                .setSubject(string)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key)
                .compact();
    }

    public String GetStringFromToken(String token) {
        try {
            String email =  Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
            return email;
        } catch (ExpiredJwtException e) {
            System.out.println("Token expired");
            return null;
        } catch (Exception e) {
            System.out.println("Invalid token");
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
            System.out.println("Token expired");
            return false;
        } catch (Exception e) {
            System.out.println("Invalid token");
            return false;
        }
    }

}
