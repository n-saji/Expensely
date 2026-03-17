package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.ApiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, UUID> {
}

