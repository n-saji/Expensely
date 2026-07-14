package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.*;
import com.example.expensely_backend.model.ReminderPriority;
import com.example.expensely_backend.model.ReminderStatus;
import com.example.expensely_backend.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping
    public ResponseEntity<ReminderResponse> createReminder(
            Authentication authentication,
            @Valid @RequestBody CreateReminderRequest request) {
        String principal = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal);
        ReminderResponse response = reminderService.createReminder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ReminderSummaryResponse>> getReminders(
            Authentication authentication,
            @RequestParam(required = false) ReminderStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ReminderPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "dueAt") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        String principal = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<ReminderSummaryResponse> response = reminderService.getReminders(userId, status, categoryId, priority, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReminderResponse> getReminderById(
            Authentication authentication,
            @PathVariable UUID id) {
        String principal = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal);
        ReminderResponse response = reminderService.getReminder(id, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReminderResponse> updateReminder(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReminderRequest request) {
        String principal = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal);
        ReminderResponse response = reminderService.updateReminder(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReminder(
            Authentication authentication,
            @PathVariable UUID id) {
        String principal = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal);
        reminderService.softDeleteReminder(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<ReminderResponse> completeReminder(
            Authentication authentication,
            @PathVariable UUID id) {
        String principal = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal);
        ReminderResponse response = reminderService.completeReminder(id, userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/snooze")
    public ResponseEntity<ReminderResponse> snoozeReminder(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody SnoozeReminderRequest request) {
        String principal = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal);
        ReminderResponse response = reminderService.snoozeReminder(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/notifications")
    public ResponseEntity<List<NotificationResponse>> getReminderNotifications(
            Authentication authentication,
            @PathVariable UUID id) {
        String principal = (String) authentication.getPrincipal();
        UUID userId = UUID.fromString(principal);
        List<NotificationResponse> response = reminderService.getReminderNotifications(id, userId);
        return ResponseEntity.ok(response);
    }
}
