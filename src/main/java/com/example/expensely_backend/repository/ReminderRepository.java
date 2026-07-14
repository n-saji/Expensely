package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.Reminder;
import com.example.expensely_backend.model.ReminderPriority;
import com.example.expensely_backend.model.ReminderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {
    
    Optional<Reminder> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT r FROM Reminder r WHERE r.user.id = :userId AND r.deletedAt IS NULL " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:categoryId IS NULL OR r.category.id = :categoryId) " +
           "AND (:priority IS NULL OR r.priority = :priority)")
    Page<Reminder> findFilteredReminders(
            @Param("userId") UUID userId,
            @Param("status") ReminderStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("priority") ReminderPriority priority,
            Pageable pageable);

    List<Reminder> findByCategoryIdAndUserIdAndDeletedAtIsNull(UUID categoryId, UUID userId);

    long countByCategoryIdAndUserIdAndDeletedAtIsNull(UUID categoryId, UUID userId);

    @Query("SELECT r FROM Reminder r WHERE r.deletedAt IS NULL AND r.status != 'COMPLETED' " +
           "AND r.dueAt < :now")
    List<Reminder> findPassedReminders(@Param("now") LocalDateTime now);

    @Query("SELECT r FROM Reminder r WHERE r.deletedAt IS NULL AND r.status = 'SNOOZED'")
    List<Reminder> findActiveSnoozedReminders();
}
