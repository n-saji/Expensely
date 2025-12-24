package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.Messages;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessagesRepository extends JpaRepository<Messages, UUID> {

    List<Messages> findByUserIdAndIsDeliveredFalseAndIsSeenFalse(UUID userId);

    List<Messages> findByUserIdAndIsDelivered(UUID userId, boolean isDelivered);

    List<Messages> findByUserId(UUID userId);

    List<Messages> findByUserIdAndIsSeen(UUID userId, boolean isSeen);

    @Modifying
    @Query("""
                UPDATE Messages m
                SET m.isSeen = true
                WHERE m.userId = :userId AND m.isSeen = false
            """)
    int markAllAsSeen(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE Messages m SET m.isDelivered = true WHERE m.id = :id")
    void markDelivered(@Param("id") UUID id);

}
