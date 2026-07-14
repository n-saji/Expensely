package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.*;
import com.example.expensely_backend.model.*;
import com.example.expensely_backend.repository.*;
import com.example.expensely_backend.utils.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final ReminderNotificationRepository notificationRepository;
    private final ReminderSnoozeRepository snoozeRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public ReminderService(ReminderRepository reminderRepository,
                           ReminderNotificationRepository notificationRepository,
                           ReminderSnoozeRepository snoozeRepository,
                           UserRepository userRepository,
                           CategoryRepository categoryRepository) {
        this.reminderRepository = reminderRepository;
        this.notificationRepository = notificationRepository;
        this.snoozeRepository = snoozeRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public ReminderResponse createReminder(CreateReminderRequest req, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Reminder reminder = new Reminder();
        reminder.setUser(user);
        reminder.setTitle(req.getTitle());
        reminder.setDescription(req.getDescription());
        reminder.setCategory(category);
        reminder.setAmount(req.getAmount());
        reminder.setCurrency(req.getCurrency());
        reminder.setDueAt(LocalDateTime.ofInstant(req.getDueAt(), ZoneOffset.UTC));
        reminder.setRepeatType(req.getRepeatType());
        reminder.setPriority(req.getPriority());
        reminder.setStatus(ReminderStatus.UPCOMING);
        reminder.setNotes(req.getNotes());

        Reminder saved = reminderRepository.save(reminder);

        createNotificationsForReminder(saved, req.getNotificationOffsets(), req.getDeliveryType());

        return new ReminderResponse(saved, req.getNotificationOffsets(), req.getDeliveryType());
    }

    @Transactional
    public ReminderResponse updateReminder(UUID id, UpdateReminderRequest req, UUID userId) {
        Reminder existing = getReminderWithOwnerCheck(id, userId);

        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        existing.setTitle(req.getTitle());
        existing.setDescription(req.getDescription());
        existing.setCategory(category);
        existing.setAmount(req.getAmount());
        existing.setCurrency(req.getCurrency());
        existing.setPriority(req.getPriority());
        existing.setRepeatType(req.getRepeatType());
        existing.setNotes(req.getNotes());

        LocalDateTime reqDueAtLocal = LocalDateTime.ofInstant(req.getDueAt(), ZoneOffset.UTC);
        if (!existing.getDueAt().equals(reqDueAtLocal)) {
            if (reqDueAtLocal.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
                throw new IllegalArgumentException("Due date must not be in the past");
            }
            existing.setDueAt(reqDueAtLocal);
            // Reset status back to upcoming if it was completed/missed but rescheduled to the future
            if (existing.getStatus() == ReminderStatus.COMPLETED || existing.getStatus() == ReminderStatus.MISSED) {
                existing.setStatus(ReminderStatus.UPCOMING);
            }
        }

        Reminder saved = reminderRepository.save(existing);

        // Cancel previous notifications
        List<ReminderNotification> oldNotifications = notificationRepository.findByReminderId(saved.getId());
        for (ReminderNotification rn : oldNotifications) {
            if (rn.getStatus() == NotificationStatus.PENDING) {
                rn.setStatus(NotificationStatus.CANCELLED);
                notificationRepository.save(rn);
            }
        }

        // Generate new notifications
        createNotificationsForReminder(saved, req.getNotificationOffsets(), req.getDeliveryType());

        return new ReminderResponse(saved, req.getNotificationOffsets(), req.getDeliveryType());
    }

    @Transactional
    public ReminderResponse getReminder(UUID id, UUID userId) {
        Reminder reminder = getReminderWithOwnerCheck(id, userId);
        
        List<ReminderNotification> notifications = notificationRepository.findByReminderId(reminder.getId());
        List<NotificationOffset> offsets = notifications.stream()
                .map(ReminderNotification::getNotificationOffset)
                .distinct()
                .collect(Collectors.toList());
        
        NotificationDeliveryType deliveryType = notifications.isEmpty() ? 
                NotificationDeliveryType.IN_APP : notifications.get(0).getDeliveryType();

        return new ReminderResponse(reminder, offsets, deliveryType);
    }

    @Transactional
    public Page<ReminderSummaryResponse> getReminders(UUID userId, ReminderStatus status, UUID categoryId,
                                                      ReminderPriority priority, Pageable pageable) {
        Page<Reminder> reminders = reminderRepository.findFilteredReminders(userId, status, categoryId, priority, pageable);
        return reminders.map(ReminderSummaryResponse::new);
    }

    @Transactional
    public void softDeleteReminder(UUID id, UUID userId) {
        Reminder reminder = getReminderWithOwnerCheck(id, userId);
        reminder.setDeletedAt(LocalDateTime.now());
        reminderRepository.save(reminder);

        // Cancel pending notifications
        List<ReminderNotification> notifications = notificationRepository.findByReminderId(reminder.getId());
        for (ReminderNotification rn : notifications) {
            if (rn.getStatus() == NotificationStatus.PENDING) {
                rn.setStatus(NotificationStatus.CANCELLED);
                notificationRepository.save(rn);
            }
        }
    }

    @Transactional
    public ReminderResponse completeReminder(UUID id, UUID userId) {
        Reminder reminder = getReminderWithOwnerCheck(id, userId);
        
        if (reminder.getStatus() == ReminderStatus.COMPLETED) {
            return getReminderResponse(reminder);
        }

        // Mark as completed
        reminder.setStatus(ReminderStatus.COMPLETED);
        Reminder saved = reminderRepository.save(reminder);

        // Cancel remaining pending notifications
        List<ReminderNotification> notifications = notificationRepository.findByReminderId(saved.getId());
        for (ReminderNotification rn : notifications) {
            if (rn.getStatus() == NotificationStatus.PENDING) {
                rn.setStatus(NotificationStatus.CANCELLED);
                notificationRepository.save(rn);
            }
        }

        // If recurring, schedule the next reminder
        if (reminder.getRepeatType() != ReminderRepeatType.NONE) {
            LocalDateTime nextDueAt = calculateNextOccurrence(reminder.getDueAt(), reminder.getRepeatType());
            
            Reminder nextReminder = new Reminder();
            nextReminder.setUser(reminder.getUser());
            nextReminder.setTitle(reminder.getTitle());
            nextReminder.setDescription(reminder.getDescription());
            nextReminder.setCategory(reminder.getCategory());
            nextReminder.setAmount(reminder.getAmount());
            nextReminder.setCurrency(reminder.getCurrency());
            nextReminder.setDueAt(nextDueAt);
            nextReminder.setRepeatType(reminder.getRepeatType());
            nextReminder.setPriority(reminder.getPriority());
            nextReminder.setStatus(ReminderStatus.UPCOMING);
            nextReminder.setNotes(reminder.getNotes());

            Reminder savedNext = reminderRepository.save(nextReminder);

            // Re-create notification schedules for next occurrence
            List<NotificationOffset> offsets = notifications.stream()
                    .map(ReminderNotification::getNotificationOffset)
                    .distinct()
                    .collect(Collectors.toList());
            if (offsets.isEmpty()) {
                offsets = List.of(NotificationOffset.ONE_DAY); // Fallback
            }
            NotificationDeliveryType delType = notifications.isEmpty() ? 
                    NotificationDeliveryType.IN_APP : notifications.get(0).getDeliveryType();

            createNotificationsForReminder(savedNext, offsets, delType);
        }

        return getReminderResponse(saved);
    }

    @Transactional
    public ReminderResponse snoozeReminder(UUID id, SnoozeReminderRequest req, UUID userId) {
        Reminder reminder = getReminderWithOwnerCheck(id, userId);

        LocalDateTime snoozeTime;
        String duration = req.getDuration();

        if ("10m".equalsIgnoreCase(duration)) {
            snoozeTime = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        } else if ("30m".equalsIgnoreCase(duration)) {
            snoozeTime = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30);
        } else if ("1h".equalsIgnoreCase(duration)) {
            snoozeTime = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
        } else if ("tomorrow".equalsIgnoreCase(duration)) {
            snoozeTime = LocalDateTime.now(ZoneOffset.UTC).plusDays(1);
        } else if ("custom".equalsIgnoreCase(duration)) {
            snoozeTime = req.getCustomSnoozedUntil() != null ? LocalDateTime.ofInstant(req.getCustomSnoozedUntil(), ZoneOffset.UTC) : null;
            if (snoozeTime == null || snoozeTime.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
                throw new IllegalArgumentException("Invalid custom snooze time");
            }
        } else {
            throw new IllegalArgumentException("Unsupported snooze duration: " + duration);
        }

        reminder.setStatus(ReminderStatus.SNOOZED);
        Reminder saved = reminderRepository.save(reminder);

        ReminderSnooze snooze = new ReminderSnooze();
        snooze.setReminder(saved);
        snooze.setSnoozedUntil(snoozeTime);
        snoozeRepository.save(snooze);

        // Cancel previous pending notifications since the user has snoozed/rescheduled it
        List<ReminderNotification> notifications = notificationRepository.findByReminderId(saved.getId());
        for (ReminderNotification rn : notifications) {
            if (rn.getStatus() == NotificationStatus.PENDING) {
                rn.setStatus(NotificationStatus.CANCELLED);
                notificationRepository.save(rn);
            }
        }

        return getReminderResponse(saved);
    }

    @Transactional
    public List<NotificationResponse> getReminderNotifications(UUID id, UUID userId) {
        // Ownership check
        getReminderWithOwnerCheck(id, userId);
        return notificationRepository.findByReminderId(id).stream()
                .map(NotificationResponse::new)
                .collect(Collectors.toList());
    }

    // Helper: Verify ownership and existence
    public Reminder getReminderWithOwnerCheck(UUID id, UUID userId) {
        Reminder reminder = reminderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found"));
        if (!reminder.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Reminder not found");
        }
        return reminder;
    }

    // Helper: Map Reminder to Response
    private ReminderResponse getReminderResponse(Reminder r) {
        List<ReminderNotification> notifications = notificationRepository.findByReminderId(r.getId());
        List<NotificationOffset> offsets = notifications.stream()
                .map(ReminderNotification::getNotificationOffset)
                .distinct()
                .collect(Collectors.toList());
        NotificationDeliveryType deliveryType = notifications.isEmpty() ? 
                NotificationDeliveryType.IN_APP : notifications.get(0).getDeliveryType();
        return new ReminderResponse(r, offsets, deliveryType);
    }

    // Helper: Create scheduled notifications
    private void createNotificationsForReminder(Reminder reminder, List<NotificationOffset> offsets, NotificationDeliveryType deliveryType) {
        for (NotificationOffset offset : offsets) {
            LocalDateTime scheduledAt = reminder.getDueAt().minusMinutes(offset.getMinutes());
            
            // Only create if scheduledAt is in the future
            if (scheduledAt.isAfter(LocalDateTime.now())) {
                ReminderNotification rn = new ReminderNotification();
                rn.setReminder(reminder);
                rn.setNotificationOffset(offset);
                rn.setScheduledAt(scheduledAt);
                rn.setStatus(NotificationStatus.PENDING);
                rn.setDeliveryType(deliveryType);
                notificationRepository.save(rn);
            }
        }
    }

    // Helper: Calculate next recurrence occurrence date
    private LocalDateTime calculateNextOccurrence(LocalDateTime original, ReminderRepeatType repeatType) {
        switch (repeatType) {
            case DAILY:
                return original.plusDays(1);
            case WEEKLY:
                return original.plusWeeks(1);
            case MONTHLY:
                return original.plusMonths(1);
            case YEARLY:
                return original.plusYears(1);
            default:
                return original;
        }
    }
}
