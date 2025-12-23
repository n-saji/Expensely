package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.handler.AlertHandler;
import com.example.expensely_backend.model.Messages;
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

        boolean isOnline = alertHandler.isUserOnline(user.getId());

        if (isOnline) {
            // send via WebSocket

            alertHandler.sendAlert(user.getId(), messageDTO);
        } else {
            // save to DB to send later
            System.out.println("User offline, saving message to DB" + messageDTO.getMessage());
            Messages message = new Messages();
            message.setUserId(user.getId());
            message.setType(messageDTO.getType());
            message.setMessage(messageDTO.getMessage());
            messageRepository.save(message);
        }
    }
}
