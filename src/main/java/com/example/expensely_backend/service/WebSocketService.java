package com.example.expensely_backend.service;

import com.example.expensely_backend.handler.AlertHandler;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WebSocketService {

    public void sendAlerts(String user_id, String message) {
        UUID userUUId = UUID.fromString(user_id);
        AlertHandler.sendAlert(userUUId, message);
    }
}
