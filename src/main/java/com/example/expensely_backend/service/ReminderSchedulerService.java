package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.*;
import com.example.expensely_backend.repository.*;
import com.example.expensely_backend.utils.Mailgun;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReminderSchedulerService {

    private final ReminderRepository reminderRepository;
    private final ReminderNotificationRepository notificationRepository;
    private final ReminderSnoozeRepository snoozeRepository;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;
    private final Mailgun mailgun;
    private final ObjectMapper objectMapper;
    private final DbLogService dbLogService;

    public ReminderSchedulerService(ReminderRepository reminderRepository,
                                   ReminderNotificationRepository notificationRepository,
                                   ReminderSnoozeRepository snoozeRepository,
                                   NotificationService notificationService,
                                   WebSocketService webSocketService,
                                   Mailgun mailgun,
                                   ObjectMapper objectMapper,
                                   DbLogService dbLogService) {
        this.reminderRepository = reminderRepository;
        this.notificationRepository = notificationRepository;
        this.snoozeRepository = snoozeRepository;
        this.notificationService = notificationService;
        this.webSocketService = webSocketService;
        this.mailgun = mailgun;
        this.objectMapper = objectMapper;
        this.dbLogService = dbLogService;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void runScheduler() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        dbLogService.logMessage("scheduler", getClass().getName(), "runScheduler", "Running reminders scheduler at " + now);

        // 1. Process Scheduled Notifications
        processScheduledNotifications(now);

        // 2. Process Snoozed Reminders
        processSnoozedReminders(now);

        // 3. Process Missed Reminders
        processMissedReminders(now);
    }

    private void processScheduledNotifications(LocalDateTime now) {
        List<ReminderNotification> pending = notificationRepository.findPendingNotificationsDue(now);
        for (ReminderNotification rn : pending) {
            Reminder reminder = rn.getReminder();

            if (reminder.getDeletedAt() != null || reminder.getStatus() == ReminderStatus.COMPLETED) {
                rn.setStatus(NotificationStatus.CANCELLED);
                notificationRepository.save(rn);
                continue;
            }

            if (reminder.getStatus() == ReminderStatus.SNOOZED) {
                rn.setStatus(NotificationStatus.CANCELLED);
                notificationRepository.save(rn);
                continue;
            }

            try {
                notificationService.sendReminderAlert(rn);
                rn.setStatus(NotificationStatus.SENT);
                rn.setSentAt(now);
                
                if (reminder.getStatus() == ReminderStatus.UPCOMING) {
                    reminder.setStatus(ReminderStatus.NOTIFIED);
                    reminderRepository.save(reminder);
                }
            } catch (Exception e) {
                rn.setStatus(NotificationStatus.FAILED);
                rn.setErrorMessage(e.getMessage());
            }
            notificationRepository.save(rn);
        }
    }

    private void processSnoozedReminders(LocalDateTime now) {
        List<Reminder> snoozed = reminderRepository.findActiveSnoozedReminders();
        for (Reminder reminder : snoozed) {
            Optional<ReminderSnooze> latestSnooze = snoozeRepository.findFirstByReminderIdOrderByCreatedAtDesc(reminder.getId());
            if (latestSnooze.isPresent() && latestSnooze.get().getSnoozedUntil().isBefore(now)) {
                // Snooze elapsed! Notify user
                try {
                    sendSnoozeElapsedAlert(reminder, latestSnooze.get().getSnoozedUntil());
                    reminder.setStatus(ReminderStatus.NOTIFIED);
                    reminderRepository.save(reminder);
                } catch (Exception e) {
                    dbLogService.logError("scheduler", getClass().getName(), "processSnoozedReminders", 
                            "Failed to trigger alert for elapsed snooze on reminder " + reminder.getId(), e);
                }
            }
        }
    }

    private void processMissedReminders(LocalDateTime now) {
        List<Reminder> passed = reminderRepository.findPassedReminders(now);
        for (Reminder reminder : passed) {
            if (reminder.getStatus() == ReminderStatus.SNOOZED) {
                Optional<ReminderSnooze> latestSnooze = snoozeRepository.findFirstByReminderIdOrderByCreatedAtDesc(reminder.getId());
                if (latestSnooze.isPresent() && latestSnooze.get().getSnoozedUntil().isBefore(now)) {
                    reminder.setStatus(ReminderStatus.MISSED);
                    reminderRepository.save(reminder);
                }
            } else {
                reminder.setStatus(ReminderStatus.MISSED);
                reminderRepository.save(reminder);
            }
        }
    }

    private void sendSnoozeElapsedAlert(Reminder reminder, LocalDateTime snoozedUntil) throws Exception {
        List<ReminderNotification> notifications = notificationRepository.findByReminderId(reminder.getId());
        NotificationDeliveryType deliveryType = notifications.isEmpty() ? 
                NotificationDeliveryType.IN_APP : notifications.get(0).getDeliveryType();

        // 1. Send WebSocket / In-App
        if (deliveryType == NotificationDeliveryType.IN_APP || deliveryType == NotificationDeliveryType.BOTH) {
            Map<String, Object> reminderPayload = new HashMap<>();
            reminderPayload.put("reminderId", reminder.getId().toString());
            reminderPayload.put("title", "Snoozed Reminder: " + reminder.getTitle());
            reminderPayload.put("description", reminder.getDescription());
            reminderPayload.put("dueAt", reminder.getDueAt().toString());
            reminderPayload.put("amount", reminder.getAmount());
            reminderPayload.put("currency", reminder.getCurrency());
            reminderPayload.put("priority", reminder.getPriority().name());
            reminderPayload.put("offset", "SNOOZED");

            if (reminder.getCategory() != null) {
                Map<String, Object> catPayload = new HashMap<>();
                catPayload.put("id", reminder.getCategory().getId().toString());
                catPayload.put("name", reminder.getCategory().getName());
                catPayload.put("icon", reminder.getCategory().getIcon());
                catPayload.put("color", reminder.getCategory().getColor());
                reminderPayload.put("category", catPayload);
            }

            String jsonPayload = objectMapper.writeValueAsString(reminderPayload);

            MessageDTO messageDTO = new MessageDTO();
            messageDTO.setMessage(jsonPayload);
            messageDTO.setSender(globals.SERVER_SENDER);
            messageDTO.setType(globals.MessageType.REMINDER);

            webSocketService.sendAlerts(reminder.getUser(), messageDTO);
        }

        // 2. Send Email
        if (deliveryType == NotificationDeliveryType.EMAIL || deliveryType == NotificationDeliveryType.BOTH) {
            String to = reminder.getUser().getEmail();
            String subject = "Snoozed Reminder Due: " + reminder.getTitle();
            String htmlBody = buildSnoozeElapsedHtmlEmail(reminder, snoozedUntil);
            mailgun.sendHtmlMessage(to, subject, htmlBody);
        }
    }

    private String buildSnoozeElapsedHtmlEmail(Reminder reminder, LocalDateTime snoozedUntil) {
        String formattedDate = reminder.getDueAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a"));
        String formattedSnoozeDate = snoozedUntil.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a"));
        String categoryName = reminder.getCategory() != null ? reminder.getCategory().getName() : "Uncategorized";
        
        String descSection = "";
        if (reminder.getDescription() != null && !reminder.getDescription().isBlank()) {
            descSection = "<strong>Description:</strong> " + reminder.getDescription() + "<br/>";
        }
        
        String amountSection = "";
        if (reminder.getAmount() != null) {
            String currency = reminder.getCurrency() != null ? reminder.getCurrency() : "USD";
            amountSection = "<div class='amount'>" + currency + " " + reminder.getAmount() + "</div>";
        }

        return String.format(
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "  <style>\n" +
            "    body { font-family: 'Inter', Helvetica, Arial, sans-serif; background-color: #f8fafc; color: #1e293b; margin: 0; padding: 0; }\n" +
            "    .container { max-width: 600px; margin: 40px auto; background: #ffffff; border-radius: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); overflow: hidden; border: 1px solid #e2e8f0; }\n" +
            "    .header { background: linear-gradient(135deg, #0d9488, #0f766e); padding: 32px; text-align: center; color: #ffffff; }\n" +
            "    .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.025em; }\n" +
            "    .content { padding: 32px; }\n" +
            "    .reminder-card { background-color: #f1f5f9; border-radius: 12px; padding: 24px; margin-bottom: 24px; border: 1px solid #e2e8f0; }\n" +
            "    .title { font-size: 18px; font-weight: 600; color: #0f172a; margin-top: 0; margin-bottom: 8px; }\n" +
            "    .details { font-size: 14px; color: #475569; line-height: 1.6; }\n" +
            "    .amount { font-size: 24px; font-weight: 700; color: #0d9488; margin-top: 12px; }\n" +
            "    .footer { text-align: center; padding: 24px; font-size: 12px; color: #94a3b8; border-top: 1px solid #f1f5f9; }\n" +
            "    .button { display: inline-block; background-color: #0d9488; color: #ffffff !important; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: 600; margin-top: 16px; }\n" +
            "  </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "  <div class='container'>\n" +
            "    <div class='header'>\n" +
            "      <h1>Expensely Snoozed Reminder</h1>\n" +
            "    </div>\n" +
            "    <div class='content'>\n" +
            "      <p>Hello,</p>\n" +
            "      <p>This is an alert that your snoozed financial reminder is now due (postponed until %s):</p>\n" +
            "      <div class='reminder-card'>\n" +
            "        <div class='title'>%s (Snoozed)</div>\n" +
            "        <div class='details'>\n" +
            "          <strong>Category:</strong> %s <br/>\n" +
            "          <strong>Original Due Date:</strong> %s <br/>\n" +
            "          %s\n" +
            "        </div>\n" +
            "        %s\n" +
            "      </div>\n" +
            "      <p>Log in to your account to review or mark this reminder as completed.</p>\n" +
            "      <center><a href='https://expensely.store/reminder' class='button'>Go to Dashboard</a></center>\n" +
            "    </div>\n" +
            "    <div class='footer'>\n" +
            "      &copy; 2026 Expensely. All rights reserved.\n" +
            "    </div>\n" +
            "  </div>\n" +
            "</body>\n" +
            "</html>",
            formattedSnoozeDate,
            reminder.getTitle(),
            categoryName,
            formattedDate,
            descSection,
            amountSection
        );
    }
}
