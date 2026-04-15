package com.penmate.backend.infrastructure.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProjectWebSocketSessionRegistry。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Component
public class ProjectWebSocketSessionRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByProject = new ConcurrentHashMap<>();

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param session 入参：session
     */
    public void register(Long projectId, WebSocketSession session) {
        sessionsByProject.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param session 入参：session
     */
    public void unregister(Long projectId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByProject.get(projectId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByProject.remove(projectId);
        }
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public Set<WebSocketSession> sessions(Long projectId) {
        return sessionsByProject.getOrDefault(projectId, Set.of());
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     */
    public void cleanupClosed(Long projectId) {
        Set<WebSocketSession> sessions = sessionsByProject.get(projectId);
        if (sessions == null) {
            return;
        }
        sessions.removeIf(session -> !session.isOpen());
        if (sessions.isEmpty()) {
            sessionsByProject.remove(projectId);
        }
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     */
    public void forceCloseAll(Long projectId) {
        Set<WebSocketSession> sessions = sessionsByProject.get(projectId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                session.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
        sessionsByProject.remove(projectId);
    }
}

