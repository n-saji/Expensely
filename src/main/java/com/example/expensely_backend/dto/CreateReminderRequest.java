package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.ReminderRepeatType;
import com.example.expensely_backend.model.ReminderPriority;
import com.example.expensely_backend.model.NotificationOffset;
import com.example.expensely_backend.model.NotificationDeliveryType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class CreateReminderRequest {
    @NotBlank(message = "Title cannot be empty")
    private String title;

    private String description;

    @NotNull(message = "Due date is required")
    @Future(message = "Due date must be in the future")
    private Instant dueAt;

    @NotNull(message = "Category is required")
    private UUID categoryId;

    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be positive")
    private BigDecimal amount;

    private String currency;

    @NotNull(message = "Priority is required")
    private ReminderPriority priority;

    @NotNull(message = "Repeat type is required")
    private ReminderRepeatType repeatType;

    @NotEmpty(message = "At least one notification offset is required")
    private List<NotificationOffset> notificationOffsets;

    @NotNull(message = "Delivery type is required")
    private NotificationDeliveryType deliveryType;

    private String notes;
}
