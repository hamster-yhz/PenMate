package com.penmate.backend.domain.shared.service;

public interface RealtimeEventService {

    void publishProjectEvent(Long projectId, String eventType, Object data);
}
