package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.handler.AlertHandler;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.MessagesRepository;
import org.springframework.stereotype.Service;

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
}
