package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class AgentCheckpointService {

    private static final int INLINE_STATE_LIMIT_BYTES = 256 * 1024;

    private final AgentCheckpointRepository checkpointRepository;
    private final BusinessIdGenerator businessIdGenerator;
    private final ObjectMapper objectMapper;

    public AgentCheckpointService(AgentCheckpointRepository checkpointRepository,
                                  BusinessIdGenerator businessIdGenerator,
                                  ObjectMapper objectMapper) {
        this.checkpointRepository = checkpointRepository;
        this.businessIdGenerator = businessIdGenerator;
        this.objectMapper = objectMapper;
    }

    public void checkpointIfNeeded(AgentEvent event, AgentRuntimeState state) {
        if (!shouldCheckpoint(event, state)) {
            return;
        }
        AgentCheckpoint latest = checkpointRepository.findLatest(event.runId());
        long checkpointNo = latest == null ? 1L : latest.checkpointNo() + 1L;
        String stateJson = serializeState(state);
        int stateSizeBytes = stateJson.getBytes(StandardCharsets.UTF_8).length;
        if (stateSizeBytes > INLINE_STATE_LIMIT_BYTES) {
            stateJson = "{\"stateArtifactRequired\":true,\"stateSizeBytes\":" + stateSizeBytes + "}";
        }
        checkpointRepository.save(new AgentCheckpoint(
                businessIdGenerator.nextId(),
                event.runId(),
                checkpointNo,
                event.sequence(),
                stateJson,
                stateSizeBytes,
                null
        ));
    }

    public boolean shouldCheckpoint(AgentEvent event, AgentRuntimeState state) {
        if (event.eventType().equals("run.started")) {
            return true;
        }
        if (event.eventType().equals("context.routing.completed")) {
            return true;
        }
        if (event.eventType().equals("tool.call.waiting_approval")) {
            return true;
        }
        if (event.eventType().equals("run.completed")) {
            return true;
        }
        if (event.eventType().equals("run.failed")) {
            return true;
        }
        return event.sequence() % 15L == 0L;
    }

    private String serializeState(AgentRuntimeState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize agent runtime state", ex);
        }
    }
}
