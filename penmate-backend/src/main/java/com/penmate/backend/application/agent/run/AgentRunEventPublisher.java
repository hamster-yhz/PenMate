package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentRunEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AgentRunEventPublisher.class);
    private static final int ARTIFACT_SIZE_THRESHOLD = 64 * 1024;

    private final AgentRunEventRepository eventRepository;
    private final AgentProjectionUpdater projectionUpdater;
    private final AgentRunEventBus eventBus;
    private final ObjectMapper objectMapper;
    private final AgentArtifactRepository artifactRepository;
    private final BusinessIdGenerator businessIdGenerator;

    public AgentRunEventPublisher(AgentRunEventRepository eventRepository,
                                  AgentProjectionUpdater projectionUpdater,
                                  AgentRunEventBus eventBus,
                                  ObjectMapper objectMapper,
                                  AgentArtifactRepository artifactRepository,
                                  BusinessIdGenerator businessIdGenerator) {
        this.eventRepository = eventRepository;
        this.projectionUpdater = projectionUpdater;
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
        this.artifactRepository = artifactRepository;
        this.businessIdGenerator = businessIdGenerator;
    }

    @Transactional
    public AgentEvent publish(Long runId, String eventType, Object payload) {
        String payloadJson = toJson(withSchemaVersion(payload));
        int sizeBytes = payloadJson.getBytes(StandardCharsets.UTF_8).length;
        String storedPayload = payloadJson;

        if (sizeBytes > ARTIFACT_SIZE_THRESHOLD) {
            Long artifactId = businessIdGenerator.nextId();
            artifactRepository.save(new AgentArtifact(
                    artifactId, runId, null, eventType, payloadJson, sizeBytes, null
            ));
            storedPayload = "{\"artifactRef\":\"" + artifactId + "\",\"sizeBytes\":" + sizeBytes + "}";
        }

        AgentEvent event = eventRepository.append(runId, eventType, storedPayload);
        projectionUpdater.apply(event);
        afterCommit(() -> {
            try {
                eventBus.publish(event);
            } catch (RuntimeException ex) {
                log.warn("agent run live event publish failed after commit: runId={}, sequence={}, eventType={}",
                        event.runId(), event.sequence(), event.eventType(), ex);
            }
        });
        return event;
    }

    public void broadcastOnly(Long runId, String eventType, Object payload, long sequence) {
        String payloadJson = toJson(withSchemaVersion(payload));
        AgentEvent event = AgentEvent.forBroadcast(runId, sequence, eventType, payloadJson);
        try {
            eventBus.publish(event);
        } catch (RuntimeException ex) {
            log.warn("agent run broadcast-only event failed: runId={}, eventType={}", runId, eventType, ex);
        }
    }

    private Map<String, Object> withSchemaVersion(Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", 1);
        if (payload instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && !"schemaVersion".equals(String.valueOf(entry.getKey()))) {
                    envelope.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return envelope;
        }
        if (payload != null) {
            envelope.put("data", payload);
        }
        return envelope;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize agent event payload", ex);
        }
    }

    private void afterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }
}