package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.TransactionFiles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface TransactionFilesRepository extends JpaRepository<TransactionFiles, UUID> {

    @Modifying
    @Transactional
    @Query("DELETE FROM TransactionFiles tf WHERE tf.expiresAt < :timestamp")
    int deleteByExpiresAtBefore(@Param("timestamp") Long timestamp);
}
