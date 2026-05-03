package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.agent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AgentTaskRuntimeUpdater {

    private final AgentRepository agentRepository;

    public void updateGenerationRuntime(Long projectId,
                                        Long taskId,
                                        String promptSnapshot,
                                        String generatedText,
                                        String traceId) {
        String tokenUsageJson = "{\"inputTokens\":" + safeLength(promptSnapshot) + ",\"outputTokens\":" + safeLength(generatedText) + "}";
        String costJson = "{\"currency\":\"USD\",\"estimated\":"
                + String.format(Locale.ROOT, "%.6f", estimateCost(generatedText)) + "}";
        agentRepository.updateGenerationTaskRuntime(projectId, taskId, tokenUsageJson, costJson, traceId);
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private double estimateCost(String generatedText) {
        return safeLength(generatedText) * 0.000002D;
    }
}
