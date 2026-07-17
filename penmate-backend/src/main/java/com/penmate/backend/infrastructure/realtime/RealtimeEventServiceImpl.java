package com.penmate.backend.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class RealtimeEventServiceImpl implements RealtimeEventService {

    private final ProjectWebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public RealtimeEventServiceImpl(ProjectWebSocketSessionRegistry sessionRegistry,
                                    ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishProjectEvent(Long projectId, String eventType, Object data) {
        if (projectId == null || eventType == null || eventType.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventType);
        payload.put("projectId", projectId);
        payload.put("timestamp", Instant.now().toString());
        payload.put("data", data);
        broadcast(projectId, payload);
    }

    private void broadcast(Long projectId, Map<String, Object> payload) {
        sessionRegistry.cleanupClosed(projectId);
        Set<WebSocketSession> sessions = sessionRegistry.sessions(projectId);
        if (sessions.isEmpty()) {
            return;
        }
        String text;
        try {
            text = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return;
        }
        sessions.removeIf(session -> !safeSend(session, text));
    }

    private boolean safeSend(WebSocketSession session, String text) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(text));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
