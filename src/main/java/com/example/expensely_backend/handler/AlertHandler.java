package com.example.expensely_backend.handler;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.Messages;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.MessagesRepository;
import com.example.expensely_backend.service.DbLogService;
import com.example.expensely_backend.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class AlertHandler extends TextWebSocketHandler {

    private final Map<UUID, List<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    private final MessagesRepository messageRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final DbLogService dbLogService;


    public AlertHandler(MessagesRepository messageRepository, UserService userService, ObjectMapper objectMapper,
                        DbLogService dbLogService) {
        this.messageRepository = messageRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.dbLogService = dbLogService;
    }

    public boolean isUserOnline(UUID userId) {
        List<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public void sendAlert(UUID userId, MessageDTO messageDTO) {

        List<WebSocketSession> sessions = userSessions.get(userId);
        Messages message = new Messages();
        message.setUserId(userId);
        message.setMessage(messageDTO.getMessage());
        message.setType(messageDTO.getType());
        Messages savedMsg = messageRepository.save(message);
        messageDTO.setId(savedMsg.getId().toString());
        messageDTO.setIsRead(savedMsg.isSeen());

        boolean delivered = false;

        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) continue;

                try {
                    session.sendMessage(
                            new TextMessage(objectMapper.writeValueAsString(messageDTO))
                    );
                    delivered = true;
                } catch (IOException e) {
                    dbLogService.logError("handler", getClass().getName(), "sendAlert",
                            "Failed to send WS message to user " + userId + ", error: " + e.getMessage(), e);
                }
            }
            if (delivered) {
                dbLogService.logMessage("handler", getClass().getName(), "sendAlert",
                        "running markDelivered for message: " + savedMsg.getId());
                try {
                    messageRepository.markDelivered(savedMsg.getId());
                } catch (Exception e) {
                    dbLogService.logError("handler", getClass().getName(), "sendAlert",
                            "Error marking message as delivered: " + e.getMessage(), e);
                }
            }
        } else {
            // user offline, save message to DB
            dbLogService.logMessage("handler", getClass().getName(), "sendAlert",
                    "User offline, saving message to DB: " + userId);
            savedMsg.setDelivered(false);
            messageRepository.save(savedMsg);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!session.getUri().getQuery().contains("uuid=")) {
            session.close();
            return;
        }
        String uuidParam = session.getUri().getQuery().split("=")[1]; // /ws/alerts?uuid=xxxx
        UUID userId = UUID.fromString(uuidParam);
        userSessions.computeIfAbsent(userId, k -> new ArrayList<>()).add(session);
        try {
            User user = userService.GetUserById(userId.toString());
            List<Messages> pendingMessages =
                    messageRepository.findByUserId(user.getId());

            dbLogService.logMessage("handler", getClass().getName(), "afterConnectionEstablished",
                    "User connected: " + user.getId() + ", user email: " + user.getEmail());
            for (Messages m : pendingMessages) {
                MessageDTO dto = new MessageDTO();
                dto.setMessage(m.getMessage());
                dto.setSender(globals.SERVER_SENDER);
                dto.setType(m.getType());
                dto.setTime(m.getCreatedAt());
                dto.setId(m.getId().toString());
                dto.setIsRead(m.isSeen());
                session.sendMessage(
                        new TextMessage(objectMapper.writeValueAsString(dto))
                );

                m.setDelivered(true);
            }
            messageRepository.saveAll(pendingMessages);
        } catch (Exception e) {
            dbLogService.logError("handler", getClass().getName(), "afterConnectionEstablished",
                    "Error connecting user: " + e.getMessage(), e);
            session.close();
        }

    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        userSessions.values().remove(session);
        dbLogService.logMessage("handler", getClass().getName(), "afterConnectionClosed",
                "User disconnected: " + session.getId());
    }

    public void broadcast(MessageDTO messageDTO) {
        userSessions.forEach((userId, sessions) -> sendAlert(userId, messageDTO));
    }

}
