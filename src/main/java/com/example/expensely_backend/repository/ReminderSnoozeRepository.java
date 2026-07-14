package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.ReminderSnooze;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReminderSnoozeRepository extends JpaRepository<ReminderSnooze, UUID> {
    
    List<ReminderSnooze> findByReminderIdOrderByCreatedAtDesc(UUID reminderId);

    Optional<ReminderSnooze> findFirstByReminderIdOrderByCreatedAtDesc(UUID reminderId);
}
