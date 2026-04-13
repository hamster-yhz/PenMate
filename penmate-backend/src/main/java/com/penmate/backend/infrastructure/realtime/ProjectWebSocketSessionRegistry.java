package com.penmate.backend.infrastructure.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProjectWebSocketSessionRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByProject = new ConcurrentHashMap<>();

    public void register(Long projectId, WebSocketSession session) {
        sessionsByProject.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

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

    public Set<WebSocketSession> sessions(Long projectId) {
        return sessionsByProject.getOrDefault(projectId, Set.of());
    }

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

