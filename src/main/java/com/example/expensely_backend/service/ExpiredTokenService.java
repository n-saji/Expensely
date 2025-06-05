package com.example.expensely_backend.service;


import com.example.expensely_backend.model.ExpiredToken;
import com.example.expensely_backend.repository.ExpiredTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class ExpiredTokenService {

    private final ExpiredTokenRepository expiredTokenRepository;
    public ExpiredTokenService(ExpiredTokenRepository expiredTokenRepository) {
        this.expiredTokenRepository = expiredTokenRepository;
    }

    public void saveExpiredToken(ExpiredToken token) {
        // Logic to save the expired token
        // This could involve saving it to a database or cache

        expiredTokenRepository.save(token);
    }

    public boolean isTokenExpired(String token) {
        // Logic to check if the token is expired
        // This could involve checking against a database or cache

        return expiredTokenRepository.existsById(token);
    }
}
