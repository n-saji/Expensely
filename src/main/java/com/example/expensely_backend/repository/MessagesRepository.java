package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.Messages;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessagesRepository extends JpaRepository<Messages, UUID> {

    List<Messages> findByUserIdAndIsDeliveredFalseAndIsSeenFalse(UUID userId);

    List<Messages> findByUserIdAndIsDelivered(UUID userId, boolean isDelivered);

    List<Messages> findByUserId(UUID userId);
}
