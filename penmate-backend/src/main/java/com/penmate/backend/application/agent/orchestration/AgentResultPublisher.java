package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.shared.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentResultPublisher {

    private final RealtimeEventService realtimeEventService;

    public void publishGenerationTokens(Long projectId, Long taskId, String generatedText, String traceId) {
        List<String> chunks = splitToChunks(generatedText);
        log.info("agent.sse.token.publish.start: projectId={}, taskId={}, traceId={}, chunkCount={}",
                projectId,
                taskId,
                traceId,
                chunks.size());
        int tokenIndex = 0;
        for (String token : chunks) {
            tokenIndex += 1;
            if (tokenIndex == 1) {
                log.info("agent.sse.token.publish.first: projectId={}, taskId={}, traceId={}, firstChunkLength={}",
                        projectId,
                        taskId,
                        traceId,
                        safeLength(token));
            }
            realtimeEventService.publishGenerationToken(projectId, taskId, token, false);
        }
        log.info("agent.sse.token.publish.end: projectId={}, taskId={}, traceId={}, publishedChunkCount={}",
                projectId,
                taskId,
                traceId,
                tokenIndex);
    }

    List<String> splitToChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String safeText = text == null ? "" : text;
        int step = 12;
        for (int i = 0; i < safeText.length(); i += step) {
            chunks.add(safeText.substring(i, Math.min(i + step, safeText.length())));
        }
        if (chunks.isEmpty()) {
            chunks.add("");
        }
        return chunks;
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }
}
