package com.penmate.backend.infrastructure.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 项目实时事件 WebSocket 处理器。
 * <p>负责连接建立时的项目路由解析、会话注册，以及连接关闭/异常时的会话回收。</p>
 */
@Component
public class ProjectWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_PROJECT_ID = "projectId";

    private final ProjectWebSocketSessionRegistry sessionRegistry;

    public ProjectWebSocketHandler(ProjectWebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * 处理连接建立。
     * <p>流程：从 URL 解析 projectId -> 写入会话属性 -> 注册到项目会话中心。</p>
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
     * 处理连接关闭。
     * <p>流程：从会话属性读取 projectId 并反注册会话，避免无效连接残留。</p>
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long projectId = (Long) session.getAttributes().get(ATTR_PROJECT_ID);
        if (projectId != null) {
            sessionRegistry.unregister(projectId, session);
        }
    }

    /**
     * 处理传输异常。
     * <p>流程：异常发生时立即从注册中心移除该会话，防止后续广播失败放大。</p>
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long projectId = (Long) session.getAttributes().get(ATTR_PROJECT_ID);
        if (projectId != null) {
            sessionRegistry.unregister(projectId, session);
        }
    }

    /**
     * 从连接 URL 解析项目ID。
     * <p>约定路径形态：`/ws/projects/{projectId}`。</p>
     */
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

