package com.penmate.backend.infrastructure.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * ProjectWebSocketHandler。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Component
public class ProjectWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_PROJECT_ID = "projectId";

    private final ProjectWebSocketSessionRegistry sessionRegistry;

    public ProjectWebSocketHandler(ProjectWebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * 处理业务请求。
     *
     * @param session 入参：session
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long projectId = parseProjectId(session);
        if (projectId == null) {
            return;
        }
        session.getAttributes().put(ATTR_PROJECT_ID, projectId);
        sessionRegistry.register(projectId, session);
    }

    /**
     * 处理业务请求。
     *
     * @param session 入参：session
     * @param status 入参：status
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long projectId = (Long) session.getAttributes().get(ATTR_PROJECT_ID);
        if (projectId != null) {
            sessionRegistry.unregister(projectId, session);
        }
    }

    /**
     * 处理业务请求。
     *
     * @param session 入参：session
     * @param exception 入参：exception
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long projectId = (Long) session.getAttributes().get(ATTR_PROJECT_ID);
        if (projectId != null) {
            sessionRegistry.unregister(projectId, session);
        }
    }

    private Long parseProjectId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx == path.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(path.substring(idx + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

