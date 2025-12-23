package com.example.expensely_backend.controller;

import com.example.expensely_backend.service.WebSocketService;
import com.example.expensely_backend.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/web_sockets")
public class WebSocketController {
    private final WebSocketService webSocketService;
    private final CookieUtils cookieUtils;

    public WebSocketController(WebSocketService webSocketService, CookieUtils cookieUtils) {
        this.webSocketService = webSocketService;
        this.cookieUtils = cookieUtils;
    }

    @PostMapping("/send_alert")
    public ResponseEntity<?> sendAlert(HttpServletRequest httpReq, @RequestBody String message) {
        String userId = cookieUtils.getCookie(httpReq);
        if (userId == null)
            return ResponseEntity.status(401).body("Refresh token missing");
        System.out.println(message + ": hello");
        webSocketService.sendAlerts(userId, message);
        return ResponseEntity.ok().build();
    }

}

