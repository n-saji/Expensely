package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.ReminderNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReminderNotificationRepository extends JpaRepository<ReminderNotification, UUID> {
    
    List<ReminderNotification> findByReminderId(UUID reminderId);

    @Query("SELECT rn FROM ReminderNotification rn JOIN FETCH rn.reminder r " +
           "WHERE rn.status = 'PENDING' AND rn.scheduledAt <= :now " +
           "AND r.deletedAt IS NULL AND r.status != 'COMPLETED'")
    List<ReminderNotification> findPendingNotificationsDue(@Param("now") LocalDateTime now);
}
