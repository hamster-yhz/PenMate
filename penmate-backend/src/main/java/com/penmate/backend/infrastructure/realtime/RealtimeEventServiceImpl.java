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
    private final GenerationSseEmitterHub generationSseEmitterHub;
    private final ObjectMapper objectMapper;

    public RealtimeEventServiceImpl(ProjectWebSocketSessionRegistry sessionRegistry,
                                    GenerationSseEmitterHub generationSseEmitterHub,
                                    ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.generationSseEmitterHub = generationSseEmitterHub;
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

    @Override
    public void publishGenerationToken(Long projectId, Long taskId, String token, boolean done) {
        Map<String, Object> tokenPayload = new LinkedHashMap<>();
        tokenPayload.put("taskId", taskId);
        tokenPayload.put("token", token);
        tokenPayload.put("done", done);

        publishProjectEvent(projectId, "generation.token", tokenPayload);
        generationSseEmitterHub.publish(taskId, "generation.token", tokenPayload);
        if (done) {
            generationSseEmitterHub.publish(taskId, "done", Map.of("taskId", taskId, "status", "done"));
            generationSseEmitterHub.complete(taskId);
        }
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
        } catch (JsonProcessingException e) {
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

