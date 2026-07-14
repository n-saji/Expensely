package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Reminder;
import com.example.expensely_backend.model.ReminderPriority;
import com.example.expensely_backend.model.ReminderStatus;
import com.example.expensely_backend.model.ReminderRepeatType;
import com.example.expensely_backend.model.NotificationOffset;
import com.example.expensely_backend.model.NotificationDeliveryType;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class ReminderResponse {
    private UUID id;
    private UUID userId;
    private String title;
    private String description;
    private CategoryInfo category;
    private BigDecimal amount;
    private String currency;
    private Instant dueAt;
    private ReminderRepeatType repeatType;
    private ReminderPriority priority;
    private ReminderStatus status;
    private String notes;
    private List<NotificationOffset> notificationOffsets;
    private NotificationDeliveryType deliveryType;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    public static class CategoryInfo {
        private UUID id;
        private String name;
        private String type;
        private String icon;
        private String color;
    }

    public ReminderResponse(Reminder r, List<NotificationOffset> offsets, NotificationDeliveryType deliveryType) {
        this.id = r.getId();
        this.userId = r.getUser().getId();
        this.title = r.getTitle();
        this.description = r.getDescription();
        
        if (r.getCategory() != null) {
            CategoryInfo catInfo = new CategoryInfo();
            catInfo.setId(r.getCategory().getId());
            catInfo.setName(r.getCategory().getName());
            catInfo.setType(r.getCategory().getType());
            catInfo.setIcon(r.getCategory().getIcon());
            catInfo.setColor(r.getCategory().getColor());
            this.category = catInfo;
        }

        this.amount = r.getAmount();
        this.currency = r.getCurrency();
        this.dueAt = r.getDueAt() != null ? r.getDueAt().toInstant(ZoneOffset.UTC) : null;
        this.repeatType = r.getRepeatType();
        this.priority = r.getPriority();
        this.status = r.getStatus();
        this.notes = r.getNotes();
        this.notificationOffsets = offsets;
        this.deliveryType = deliveryType;
        this.createdAt = r.getCreatedAt() != null ? r.getCreatedAt().toInstant(ZoneOffset.UTC) : null;
        this.updatedAt = r.getUpdatedAt() != null ? r.getUpdatedAt().toInstant(ZoneOffset.UTC) : null;
    }
}
