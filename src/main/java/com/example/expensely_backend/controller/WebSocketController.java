package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.UserService;
import com.example.expensely_backend.service.WebSocketService;
import com.example.expensely_backend.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/web_sockets")
public class WebSocketController {
    private final WebSocketService webSocketService;
    private final CookieUtils cookieUtils;
    private final UserService userService;

    public WebSocketController(WebSocketService webSocketService, CookieUtils cookieUtils, UserService userService) {
        this.webSocketService = webSocketService;
        this.cookieUtils = cookieUtils;
        this.userService = userService;
    }

    //    sends alert to current user
    @PostMapping("/send_alert")
    public ResponseEntity<?> sendAlert(HttpServletRequest httpReq, @RequestBody MessageDTO message) {
        String userId = cookieUtils.getCookie(httpReq);
        if (userId == null)
            return ResponseEntity.status(401).body("Refresh token missing");
        User user = userService.GetUserById(userId);
        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }
        webSocketService.sendAlerts(user, message);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/alerts/delete_by_id/{id}")
    public ResponseEntity<?> deleteById(HttpServletRequest httpReq, @PathVariable String id) {
        String userId = cookieUtils.getCookie(httpReq);
        if (userId == null)
            return ResponseEntity.status(401).body("Refresh token missing");
        User user = userService.GetUserById(userId);
        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }
        try {
            webSocketService.deleteAlert(id);
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Invalid alert id");
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PutMapping("/alerts/mark_all_read/by_user_id/{id}")
    public ResponseEntity<?> markAllAsRead(HttpServletRequest httpReq, @PathVariable String id) {
        String userId = cookieUtils.getCookie(httpReq);
        if (userId == null)
            return ResponseEntity.status(401).body("Refresh token missing");
        User user = userService.GetUserById(userId);
        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }
        try {
            webSocketService.markAllMessagesAsReadForUser(id);
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Invalid alert id");
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PutMapping("alerts/mark_as_read/by_message_id/{id}")
    public ResponseEntity<?> markAsRead(HttpServletRequest httpReq, @PathVariable String id) {
        String userId = cookieUtils.getCookie(httpReq);
        if (userId == null)
            return ResponseEntity.status(401).body("Refresh token missing");
        User user = userService.GetUserById(userId);
        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }
        try {
            webSocketService.markMessageAsRead(id);
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Invalid alert id");
        }
        return ResponseEntity.status(HttpStatus.OK).build();
    }


}

