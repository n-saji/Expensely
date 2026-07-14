package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.ReminderNotification;
import com.example.expensely_backend.model.NotificationOffset;
import com.example.expensely_backend.model.NotificationStatus;
import com.example.expensely_backend.model.NotificationDeliveryType;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class NotificationResponse {
    private UUID id;
    private UUID reminderId;
    private NotificationOffset notificationOffset;
    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private NotificationStatus status;
    private NotificationDeliveryType deliveryType;
    private String errorMessage;

    public NotificationResponse(ReminderNotification rn) {
        this.id = rn.getId();
        this.reminderId = rn.getReminder().getId();
        this.notificationOffset = rn.getNotificationOffset();
        this.scheduledAt = rn.getScheduledAt();
        this.sentAt = rn.getSentAt();
        this.status = rn.getStatus();
        this.deliveryType = rn.getDeliveryType();
        this.errorMessage = rn.getErrorMessage();
    }
}
