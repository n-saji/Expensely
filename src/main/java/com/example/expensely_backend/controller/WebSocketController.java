package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.model.Messages;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.MessagesRepository;
import com.example.expensely_backend.service.ResourceAccess;
import com.example.expensely_backend.service.UserService;
import com.example.expensely_backend.service.WebSocketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/web_sockets")
public class WebSocketController {
    private final WebSocketService sockets;
    private final UserService users;
    private final MessagesRepository messages;
    private final ResourceAccess access;

    public WebSocketController(WebSocketService sockets, UserService users, MessagesRepository messages, ResourceAccess access) {
        this.sockets = sockets;
        this.users = users;
        this.messages = messages;
        this.access = access;
    }

    @PostMapping("/send_alert")
    public ResponseEntity<?> sendAlert(Authentication authentication, @RequestBody MessageDTO message) {
        User user = users.GetActiveUserById(access.userId(authentication).toString());
        sockets.sendAlerts(user, message);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/alerts/delete_by_id/{id}")
    public ResponseEntity<?> deleteById(Authentication authentication, @PathVariable String id) {
        ownedMessage(authentication, id);
        sockets.deleteAlert(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/alerts/mark_all_read/by_user_id/{id}")
    public ResponseEntity<?> markAllAsRead(Authentication authentication, @PathVariable String id) {
        access.sameUser(authentication, id);
        sockets.markAllMessagesAsReadForUser(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/alerts/mark_as_read/by_message_id/{id}")
    public ResponseEntity<?> markAsRead(Authentication authentication, @PathVariable String id) {
        ownedMessage(authentication, id);
        sockets.markMessageAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/alerts/broadcast")
    public ResponseEntity<?> broadcast(Authentication authentication, @RequestParam("msg") String message) {
        access.admin(authentication);
        sockets.sendBroadCastMessage(message);
        return ResponseEntity.ok().build();
    }

    private void ownedMessage(Authentication authentication, String id) {
        UUID messageId;
        try { messageId = UUID.fromString(id); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid message ID"); }
        Messages message = messages.findById(messageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        access.owned(authentication, message.getUserId());
    }
}
