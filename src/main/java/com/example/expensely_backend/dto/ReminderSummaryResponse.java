package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Reminder;
import com.example.expensely_backend.model.ReminderPriority;
import com.example.expensely_backend.model.ReminderStatus;
import com.example.expensely_backend.model.ReminderRepeatType;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Data
@NoArgsConstructor
public class ReminderSummaryResponse {
    private UUID id;
    private String title;
    private Instant dueAt;
    private ReminderResponse.CategoryInfo category;
    private BigDecimal amount;
    private String currency;
    private ReminderPriority priority;
    private ReminderStatus status;
    private ReminderRepeatType repeatType;

    public ReminderSummaryResponse(Reminder r) {
        this.id = r.getId();
        this.title = r.getTitle();
        this.dueAt = r.getDueAt() != null ? r.getDueAt().toInstant(ZoneOffset.UTC) : null;
        
        if (r.getCategory() != null) {
            ReminderResponse.CategoryInfo catInfo = new ReminderResponse.CategoryInfo();
            catInfo.setId(r.getCategory().getId());
            catInfo.setName(r.getCategory().getName());
            catInfo.setType(r.getCategory().getType());
            catInfo.setIcon(r.getCategory().getIcon());
            catInfo.setColor(r.getCategory().getColor());
            this.category = catInfo;
        }

        this.amount = r.getAmount();
        this.currency = r.getCurrency();
        this.priority = r.getPriority();
        this.status = r.getStatus();
        this.repeatType = r.getRepeatType();
    }
}
