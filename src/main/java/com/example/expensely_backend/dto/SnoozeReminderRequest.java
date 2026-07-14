package com.example.expensely_backend.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class SnoozeReminderRequest {
    private String duration; // "10m", "30m", "1h", "tomorrow", or "custom"
    private Instant customSnoozedUntil;
}
