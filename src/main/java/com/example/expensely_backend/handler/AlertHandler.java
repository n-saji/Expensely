package com.example.expensely_backend.handler;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.Messages;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.MessagesRepository;
import com.example.expensely_backend.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

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


    public AlertHandler(MessagesRepository messageRepository, UserService userService, ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    public boolean isUserOnline(UUID userId) {
        List<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public void sendAlert(UUID userId, MessageDTO alertMessage) {

        List<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(alertMessage)));
                    }
                } catch (Exception e) {
                    System.out.println("Error sending alert to user: " + userId);
                }
            }
        } else {
            // user offline, save message to DB
            Messages message = new Messages();
            message.setUserId(userId);
            message.setMessage(alertMessage.getMessage());
            message.setType(alertMessage.getType());
            message.setDelivered(false);
            messageRepository.save(message);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!session.getUri().getQuery().contains("uuid=")) {
            session.close();
        }
        String uuidParam = session.getUri().getQuery().split("=")[1]; // /ws/alerts?uuid=xxxx
        UUID userId = UUID.fromString(uuidParam);
        userSessions.computeIfAbsent(userId, k -> new ArrayList<>()).add(session);
        try {
            User user = userService.GetUserById(uuidParam);
            List<Messages> pendingMessages =
                    messageRepository.findByUserIdAndIsDeliveredFalseAndIsSeenFalse(user.getId());

            System.out.println("User connected: " + user.getId() + ", user email:  " + user.getEmail());
            for (Messages m : pendingMessages) {
                MessageDTO dto = new MessageDTO();
                dto.setMessage(m.getMessage());
                dto.setSender(globals.SERVER_SENDER);
                dto.setType(m.getType());
                dto.setTime(m.getCreatedAt());
                sendAlert(userId, dto);
                m.setDelivered(true);
            }
            messageRepository.saveAll(pendingMessages);
        } catch (Exception e) {
            System.out.println("Error connecting user: " + e.getMessage());
            session.close();
        }

    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        userSessions.values().remove(session);
        System.out.println("User disconnected: " + session.getId());
    }

    public void broadcast(MessageDTO messageDTO) {
        for (List<WebSocketSession> sessions : userSessions.values()) {
            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(messageDTO)));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
