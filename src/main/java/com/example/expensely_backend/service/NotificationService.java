package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.Reminder;
import com.example.expensely_backend.model.ReminderNotification;
import com.example.expensely_backend.utils.Mailgun;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    private final WebSocketService webSocketService;
    private final Mailgun mailgun;
    private final ObjectMapper objectMapper;
    private final DbLogService dbLogService;

    public NotificationService(WebSocketService webSocketService, Mailgun mailgun,
                               ObjectMapper objectMapper, DbLogService dbLogService) {
        this.webSocketService = webSocketService;
        this.mailgun = mailgun;
        this.objectMapper = objectMapper;
        this.dbLogService = dbLogService;
    }

    public void sendReminderAlert(ReminderNotification notification) {
        Reminder reminder = notification.getReminder();

        // 1. Send WebSocket / In-App if delivery type is IN_APP or BOTH and user has in-app notifications enabled
        if ((notification.getDeliveryType() == com.example.expensely_backend.model.NotificationDeliveryType.IN_APP ||
             notification.getDeliveryType() == com.example.expensely_backend.model.NotificationDeliveryType.BOTH) &&
            Boolean.TRUE.equals(reminder.getUser().getInAppNotificationsEnabled())) {
            try {
                // Serialize details as JSON so the frontend notification component can parse it
                Map<String, Object> reminderPayload = new HashMap<>();
                reminderPayload.put("reminderId", reminder.getId().toString());
                reminderPayload.put("title", reminder.getTitle());
                reminderPayload.put("description", reminder.getDescription());
                reminderPayload.put("dueAt", reminder.getDueAt().atZone(java.time.ZoneOffset.UTC).toInstant().toString());
                reminderPayload.put("amount", reminder.getAmount());
                reminderPayload.put("currency", reminder.getCurrency());
                reminderPayload.put("priority", reminder.getPriority().name());
                reminderPayload.put("offset", notification.getNotificationOffset().name());

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
                dbLogService.logMessage("service", getClass().getName(), "sendReminderAlert",
                        "WS reminder notification sent for: " + reminder.getId());
            } catch (Exception e) {
                dbLogService.logError("service", getClass().getName(), "sendReminderAlert",
                        "Error sending WS alert: " + e.getMessage(), e);
            }
        }

        // 2. Send Email if delivery type is EMAIL or BOTH and user has email notifications enabled
        if ((notification.getDeliveryType() == com.example.expensely_backend.model.NotificationDeliveryType.EMAIL ||
             notification.getDeliveryType() == com.example.expensely_backend.model.NotificationDeliveryType.BOTH) &&
            Boolean.TRUE.equals(reminder.getUser().getEmailNotificationsEnabled())) {
            try {
                String to = reminder.getUser().getEmail();
                String subject = "Upcoming Financial Reminder: " + reminder.getTitle();
                String htmlBody = buildReminderHtmlEmail(reminder);

                mailgun.sendHtmlMessage(to, subject, htmlBody);
                dbLogService.logMessage("service", getClass().getName(), "sendReminderAlert",
                        "HTML Email reminder sent for: " + reminder.getId());
            } catch (Exception e) {
                dbLogService.logError("service", getClass().getName(), "sendReminderAlert",
                        "Error sending Email alert: " + e.getMessage(), e);
                throw e; // Propagate exception to scheduler to log failure
            }
        }
    }

    private String buildReminderHtmlEmail(Reminder reminder) {
        String formattedDate = reminder.getDueAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a")) + " UTC";
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
            "      <h1>Expensely Reminder Alert</h1>\n" +
            "    </div>\n" +
            "    <div class='content'>\n" +
            "      <p>Hello,</p>\n" +
            "      <p>This is a scheduled reminder that your financial event is upcoming:</p>\n" +
            "      <div class='reminder-card'>\n" +
            "        <div class='title'>%s</div>\n" +
            "        <div class='details'>\n" +
            "          <strong>Category:</strong> %s <br/>\n" +
            "          <strong>Due Date:</strong> %s <br/>\n" +
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
            reminder.getTitle(),
            categoryName,
            formattedDate,
            descSection,
            amountSection
        );
    }
}
