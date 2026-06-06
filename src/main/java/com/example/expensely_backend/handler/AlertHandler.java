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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class AlertHandler extends TextWebSocketHandler {

	private final Map<UUID, Map<String, WebSocketSession>> userSessions = new ConcurrentHashMap<>();

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

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {

		String query = session.getUri().getQuery();
		Map<String, String> params = parseQueryParams(query);

		String uuidParam = params.get("uuid");
		String refreshToken = params.get("refreshToken");

		// /ws/alerts?uuid=xxxx&refreshToken=
		if (uuidParam == null || refreshToken == null) {
			session.close();
			return;
		}
		UUID userId = UUID.fromString(uuidParam);
		userSessions
				.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
				.put(refreshToken, session);
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
		String query = session.getUri().getQuery();
		Map<String, String> params = parseQueryParams(query);

		String uuidParam = params.get("uuid");
		String refreshToken = params.get("refreshToken");

		if (uuidParam == null || refreshToken == null) return;

		UUID userId = UUID.fromString(uuidParam);
		Map<String, WebSocketSession> sessions = userSessions.get(userId);
		if (sessions != null) {
			sessions.remove(refreshToken);
			if (sessions.isEmpty()) {
				userSessions.remove(userId);
			}
		}
		dbLogService.logMessage("handler", getClass().getName(), "afterConnectionClosed",
				"User disconnected: " + session.getId());
	}

	public boolean isUserOnline(UUID userId, String refreshToken) {
		Map<String, WebSocketSession> sessions = userSessions.get(userId);
		return sessions != null && sessions.containsKey(refreshToken) && sessions.get(refreshToken).isOpen();

	}

	public void sendSessionLogOut(UUID userId, String token,
	                              MessageDTO messageDTO) {
		Map<String, WebSocketSession> sessions = userSessions.get(userId);

		if (sessions != null) {
			for (String session : sessions.keySet()) {
				if (!sessions.get(session).isOpen() || !token.equals(session))
					continue;

				try {
					sessions.get(session).sendMessage(
							new TextMessage(objectMapper.writeValueAsString(messageDTO))
					);

				} catch (IOException e) {
					dbLogService.logError("handler", getClass().getName(), "sendAlert",
							"Failed to send WS message to user " + userId + ", error: " + e.getMessage(), e);
				}
			}

		}
	}

	public void sendAlert(UUID userId, MessageDTO messageDTO) {

		Map<String, WebSocketSession> sessions = userSessions.get(userId);
		Messages message = new Messages();
		message.setUserId(userId);
		message.setMessage(messageDTO.getMessage());
		message.setType(messageDTO.getType());
		Messages savedMsg = messageRepository.save(message);
		messageDTO.setId(savedMsg.getId().toString());
		messageDTO.setIsRead(savedMsg.isSeen());

		boolean delivered = false;

		if (sessions != null) {
			for (WebSocketSession session : sessions.values()) {
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


	public void broadcast(MessageDTO messageDTO) {
		userSessions.forEach((userId, sessions) -> sendAlert(userId, messageDTO));
	}


	private Map<String, String> parseQueryParams(String query) {
		Map<String, String> params = new HashMap<>();
		if (query == null) return params;
		for (String pair : query.split("&")) {
			String[] kv = pair.split("=", 2);
			if (kv.length == 2) {
				params.put(kv[0], kv[1]);
			}
		}
		return params;
	}


}
