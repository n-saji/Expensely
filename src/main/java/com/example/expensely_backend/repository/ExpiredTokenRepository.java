package com.example.expensely_backend.repository;


import com.example.expensely_backend.model.ExpiredToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpiredTokenRepository extends JpaRepository<ExpiredToken, String> {



}
