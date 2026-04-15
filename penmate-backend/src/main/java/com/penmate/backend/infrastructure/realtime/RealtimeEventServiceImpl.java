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

/**
 * RealtimeEventServiceImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
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

    /**
     * 发布业务状态。
     *
     * @param projectId 入参：projectId
     * @param eventType 入参：eventType
     * @param data 入参：data
     */
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

    /**
     * 发布业务状态。
     *
     * @param projectId 入参：projectId
     * @param taskId 入参：taskId
     * @param token 入参：token
     * @param done 入参：done
     */
    @Override
    public void publishGenerationToken(Long projectId, Long taskId, String token, boolean done) {
        Map<String, Object> tokenPayload = new LinkedHashMap<>();
        tokenPayload.put("taskId", taskId);
        tokenPayload.put("token", token);
        tokenPayload.put("done", done);

        // 复杂流程解析：同一token事件同时投递到WebSocket与SSE，兼容不同客户端订阅通道。
        publishProjectEvent(projectId, "generation.token", tokenPayload);
        generationSseEmitterHub.publish(taskId, "generation.token", tokenPayload);
        if (done) {
            generationSseEmitterHub.publish(taskId, "done", Map.of("taskId", taskId, "status", "done"));
            generationSseEmitterHub.complete(taskId);
        }
    }

    private void broadcast(Long projectId, Map<String, Object> payload) {
        // 复杂流程解析：广播前先清理失效连接，避免发送异常影响正常会话。
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

