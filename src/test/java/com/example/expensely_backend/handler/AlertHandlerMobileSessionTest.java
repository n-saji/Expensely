package com.example.expensely_backend.handler;

import com.example.expensely_backend.repository.MessagesRepository;
import com.example.expensely_backend.service.DbLogService;
import com.example.expensely_backend.service.RedisSession;
import com.example.expensely_backend.service.UserService;
import com.example.expensely_backend.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class AlertHandlerMobileSessionTest {
    @Test
    void acceptsActiveMobileRefreshTokenAndRejectsStaleToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "mobile-refresh";
        MessagesRepository messages = mock(MessagesRepository.class);
        UserService users = mock(UserService.class);
        DbLogService log = mock(DbLogService.class);
        RedisSession sessions = mock(RedisSession.class);
        JwtUtil jwt = mock(JwtUtil.class);
        Claims claims = mock(Claims.class);
        when(jwt.parseMobileToken(token, "mobile_refresh")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("sid", String.class)).thenReturn("mobile-session");
        when(messages.findByUserId(userId)).thenReturn(List.of());
        var user = new com.example.expensely_backend.model.User();
        user.setId(userId);
        when(users.GetUserById(userId.toString())).thenReturn(user);
        var handler = new AlertHandler(messages, users, new ObjectMapper(), log, sessions, jwt);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getUri()).thenReturn(URI.create("ws://localhost/ws/alerts?uuid=" + userId + "&refreshToken=" + token));
        when(socket.getAttributes()).thenReturn(new HashMap<>());
        when(sessions.isMobileRefreshActive(userId.toString(), "mobile-session", token)).thenReturn(true);
        handler.afterConnectionEstablished(socket);
        verify(socket, never()).close();
        verify(messages).findByUserId(userId);

        WebSocketSession stale = mock(WebSocketSession.class);
        when(stale.getUri()).thenReturn(URI.create("ws://localhost/ws/alerts?uuid=" + userId + "&refreshToken=" + token));
        when(sessions.isMobileRefreshActive(userId.toString(), "mobile-session", token)).thenReturn(false);
        handler.afterConnectionEstablished(stale);
        verify(stale).close();
    }
}
