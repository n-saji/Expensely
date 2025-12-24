package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.handler.AlertHandler;
import com.example.expensely_backend.model.Messages;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.MessagesRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WebSocketService {
    private final MessagesRepository messageRepository;
    private final AlertHandler alertHandler;

    public WebSocketService(MessagesRepository messageRepository, AlertHandler alertHandler) {
        this.messageRepository = messageRepository;
        this.alertHandler = alertHandler;
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
            System.out.println("Error sending websocket msg: " + e.getMessage());
        }

    }

    public void deleteAlert(String alertId) {
        if (alertId == null || alertId.isEmpty()) return;
        UUID alertUUID = UUID.fromString(alertId);
        try {
            messageRepository.deleteById(alertUUID);
        } catch (Exception e) {
            System.out.println("Error deleting alert: " + e.getMessage());
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
            System.out.println("Error marking messages as read: " + e.getMessage());
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
            System.out.println("Error marking message as read: " + e.getMessage());
        }
    }
}
