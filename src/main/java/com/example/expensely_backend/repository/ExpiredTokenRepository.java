package com.example.expensely_backend.repository;


import com.example.expensely_backend.model.ExpiredToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExpiredTokenRepository extends JpaRepository<ExpiredToken, String> {


    long deleteAllByUserId(UUID userId);
}
