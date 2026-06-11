package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class AgentCheckpointService {

    private static final Logger log = LoggerFactory.getLogger(AgentCheckpointService.class);
    private static final int INLINE_STATE_LIMIT_BYTES = 256 * 1024;
    private static final long REDIS_TTL_SECONDS = 30 * 60;
    private static final String REDIS_KEY_PREFIX = "agent:checkpoint:";

    private final AgentCheckpointRepository checkpointRepository;
    private final StringRedisTemplate redisTemplate;
    private final BusinessIdGenerator businessIdGenerator;
    private final ObjectMapper objectMapper;

    public AgentCheckpointService(AgentCheckpointRepository checkpointRepository,
                                  StringRedisTemplate redisTemplate,
                                  BusinessIdGenerator businessIdGenerator,
                                  ObjectMapper objectMapper) {
        this.checkpointRepository = checkpointRepository;
        this.redisTemplate = redisTemplate;
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
        AgentCheckpoint checkpoint = new AgentCheckpoint(
                businessIdGenerator.nextId(),
                event.runId(),
                checkpointNo,
                event.sequence(),
                stateJson,
                stateSizeBytes,
                null
        );
        checkpointRepository.save(checkpoint);
        try {
            String redisKey = REDIS_KEY_PREFIX + event.runId() + ":latest";
            redisTemplate.opsForValue().set(redisKey, stateJson, Duration.ofSeconds(REDIS_TTL_SECONDS));
        } catch (Exception ex) {
            log.warn("Failed to write checkpoint to Redis: runId={}, checkpointNo={}", event.runId(), checkpointNo, ex);
        }
    }

    public AgentRuntimeState loadLatestFromRedis(Long runId) {
        String redisKey = REDIS_KEY_PREFIX + runId + ":latest";
        try {
            String stateJson = redisTemplate.opsForValue().get(redisKey);
            if (stateJson != null && !stateJson.isBlank()) {
                return objectMapper.readValue(stateJson, AgentRuntimeState.class);
            }
        } catch (Exception ex) {
            log.warn("Failed to load checkpoint from Redis: runId={}", runId, ex);
        }
        AgentCheckpoint checkpoint = checkpointRepository.findLatest(runId);
        if (checkpoint != null && checkpoint.stateJson() != null) {
            try {
                return objectMapper.readValue(checkpoint.stateJson(), AgentRuntimeState.class);
            } catch (Exception ex) {
                log.error("Failed to deserialize checkpoint from MySQL: runId={}", runId, ex);
            }
        }
        return null;
    }

    public void deleteCheckpoints(Long runId) {
        String redisKey = REDIS_KEY_PREFIX + runId + ":latest";
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception ex) {
            log.warn("Failed to delete checkpoint from Redis: runId={}", runId, ex);
        }
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