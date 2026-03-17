package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.handler.AlertHandler;
import com.example.expensely_backend.model.Messages;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.MessagesRepository;
import com.example.expensely_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WebSocketService {
    private final MessagesRepository messageRepository;
    private final AlertHandler alertHandler;
    private final UserRepository userRepository;
    private final DbLogService dbLogService;

    public WebSocketService(MessagesRepository messageRepository, AlertHandler alertHandler, UserRepository userRepository,
                            DbLogService dbLogService) {
        this.messageRepository = messageRepository;
        this.alertHandler = alertHandler;
        this.userRepository = userRepository;
        this.dbLogService = dbLogService;
    }

    public void sendAlerts(User user, MessageDTO messageDTO) {


        if (messageDTO.getMessage() == null || messageDTO.getMessage().isEmpty()) return;
        if (messageDTO.getSender() == null || messageDTO.getSender().isEmpty()) return;
        if (messageDTO.getType() == null) return;

//db storing handled in AlertHandler
        // send via WebSocket
        try {
            alertHandler.sendAlert(user.getId(), messageDTO);
        } catch (Exception e) {
            dbLogService.logError("service", getClass().getName(), "sendAlerts",
                    "Error sending websocket msg: " + e.getMessage(), e);
        }

    }

    public void deleteAlert(String alertId) {
        if (alertId == null || alertId.isEmpty()) return;
        UUID alertUUID = UUID.fromString(alertId);
        try {
            messageRepository.deleteById(alertUUID);
        } catch (Exception e) {
            dbLogService.logError("service", getClass().getName(), "deleteAlert",
                    "Error deleting alert: " + e.getMessage(), e);
        }

    }

    public void markAllMessagesAsReadForUser(String userID) {
        if (userID == null || userID.isEmpty()) {
            return;
        }
        UUID userUUID = UUID.fromString(userID);
        try {
            messageRepository.markAllAsSeen(userUUID);
        } catch (Exception e) {
            dbLogService.logError("service", getClass().getName(), "markAllMessagesAsReadForUser",
                    "Error marking messages as read: " + e.getMessage(), e);
        }
    }

    public void markMessageAsRead(String messageID) {
        if (messageID == null || messageID.isEmpty()) {
            return;
        }
        UUID msgUUID = UUID.fromString(messageID);
        try {
            Messages msg = messageRepository.findById(msgUUID).get();
            msg.setSeen(true);
            messageRepository.save(msg);
        } catch (Exception e) {
            dbLogService.logError("service", getClass().getName(), "markMessageAsRead",
                    "Error marking message as read: " + e.getMessage(), e);
        }
    }

    public void sendBroadCastMessage(String message) {
        if (message == null || message.isEmpty()) return;
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) return;
        try {
            MessageDTO msg = new MessageDTO();
            msg.setMessage(message);
            msg.setSender(globals.SERVER_SENDER);
            msg.setType(globals.MessageType.INFO);
            for (User user : users) {
                alertHandler.sendAlert(user.getId(), msg);
            }
        } catch (Exception e) {
            dbLogService.logError("service", getClass().getName(), "sendBroadCastMessage",
                    "Error sending broadcast msg: " + e.getMessage(), e);
        }
    }
}
