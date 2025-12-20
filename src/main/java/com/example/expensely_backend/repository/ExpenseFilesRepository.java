package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.ExpenseFiles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface ExpenseFilesRepository extends JpaRepository<ExpenseFiles, UUID> {

    @Modifying
    @Transactional
    @Query("DELETE FROM ExpenseFiles ef WHERE ef.expiresAt < :timestamp")
    int deleteByExpiresAtBefore(@Param("timestamp") Long timestamp);
}
