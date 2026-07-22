package com.penmate.backend.application.model;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.model.command.ModelCommands.ProbeEmbeddingDimensionCommand;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.repository.ModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ModelConnectionTestService {
    private static final Pattern BEARER_SECRET = Pattern.compile("(?i)(bearer\\s+|sk-)[^\\s,;]+", Pattern.CASE_INSENSITIVE);

    private final ModelRepository repository;
    private final ModelApplicationService models;
    private final AgentModelRoutingService routing;
    private final AgentLlmGateway llm;

    public ConnectionTestResult test(Long actorUserId, Long modelConfigId, boolean systemScope, String traceId) {
        ModelConfiguration configuration = repository.findAccessibleConfiguration(actorUserId, modelConfigId);
        if (configuration == null || systemScope != "SYSTEM".equals(configuration.getScopeType())) {
            throw BusinessException.notFound("Model configuration not found");
        }
        if (!"ACTIVE".equalsIgnoreCase(configuration.getStatus())) {
            throw BusinessException.conflict("Disabled model configurations cannot be tested");
        }

        long started = System.nanoTime();
        Instant testedAt = Instant.now();
        try {
            Integer dimensions = null;
            if ("EMBEDDING".equals(configuration.getModelType())) {
                dimensions = models.probeEmbeddingDimensions(actorUserId, systemScope,
                        new ProbeEmbeddingDimensionCommand(modelConfigId, null, null, null, null, null)).dimensions();
            } else {
                llm.generateTurn(new AgentLlmTurnRequest(
                                List.of(AgentLlmMessage.user("Reply OK.")), List.of(), "none", Duration.ofSeconds(20)),
                        routing.resolveExecutionConfig(actorUserId, modelConfigId, traceId));
            }
            int latencyMs = elapsedMilliseconds(started);
            repository.updateConnectionTest(actorUserId, modelConfigId, systemScope,
                    "SUCCESS", latencyMs, null, testedAt);
            return new ConnectionTestResult(true, latencyMs, testedAt, null, dimensions);
        } catch (RuntimeException exception) {
            int latencyMs = elapsedMilliseconds(started);
            String error = sanitize(exception.getMessage());
            repository.updateConnectionTest(actorUserId, modelConfigId, systemScope,
                    "FAILED", latencyMs, error, testedAt);
            return new ConnectionTestResult(false, latencyMs, testedAt, error, null);
        }
    }

    private int elapsedMilliseconds(long started) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (System.nanoTime() - started) / 1_000_000L));
    }

    private String sanitize(String message) {
        String value = message == null || message.isBlank() ? "模型服务连接失败" : message;
        value = BEARER_SECRET.matcher(value.replaceAll("[\\r\\n\\t]+", " ")).replaceAll("$1****");
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    public record ConnectionTestResult(boolean success, int latencyMs, Instant testedAt,
                                       String error, Integer dimensions) {
    }
}
