package com.example.expensely_backend.handler;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AlertHandler extends TextWebSocketHandler {

    private static final Map<UUID, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    public static void sendAlert(UUID userId, String alertMessage) {
        WebSocketSession session = userSessions.get(userId);
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(alertMessage));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!session.getUri().getQuery().contains("uuid=")) {
            session.close();
        }
        String uuidParam = session.getUri().getQuery().split("=")[1]; // /ws/alerts?uuid=xxxx
        UUID userId = UUID.fromString(uuidParam);
        userSessions.put(userId, session);
        System.out.println("User connected: " + userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        System.out.println("Received: " + message.getPayload());
        // Broadcast to all sessions
        for (WebSocketSession s : userSessions.values()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage("User " + session.getId() + ": " + message.getPayload()));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        userSessions.values().remove(session);
        System.out.println("User disconnected: " + session.getId());
    }
}
