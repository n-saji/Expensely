package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reminder_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reminder_id", nullable = false)
    private Reminder reminder;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_offset", nullable = false)
    private NotificationOffset notificationOffset;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false)
    private NotificationDeliveryType deliveryType;

    @Column(name = "error_message")
    private String errorMessage;
}
