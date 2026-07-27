package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.repository.AgentQueuedRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AgentQueuedRequestExecutor {
    private static final int MAX_ATTEMPTS = 3;

    private final AgentQueuedRequestRepository requests;
    private final AgentTurnAppService turns;
    private final AgentContextCompressionService compression;
    private final JsonCodec json;

    public AgentQueuedRequestExecutor(AgentQueuedRequestRepository requests,
                                      AgentTurnAppService turns,
                                      AgentContextCompressionService compression,
                                      JsonCodec json) {
        this.requests = requests;
        this.turns = turns;
        this.compression = compression;
        this.json = json;
    }

    @Transactional
    public void executeNext() {
        var request = requests.claimNextIdle();
        if (request == null) return;
        try {
            if ("COMPRESS".equals(request.requestType())) {
                compression.compress(request.projectId(), request.sessionId(), request.ownerUserId(),
                        "queued-context-compression-" + request.requestId());
            } else {
                var payload = json.read(request.payloadJson(), AgentQueuedRequestApplicationService.QueueMessagePayload.class);
                var task = payload.taskRequest();
                turns.createTurn(request.projectId(), request.sessionId(), new AgentTurnCommand(
                        request.ownerUserId(), payload.userMessage(), payload.activeSkills(),
                        task == null ? null : new AgentTurnCommand.TaskRequest(
                                task.chapterId(), task.chapterIds(), task.modelConfigId(), task.selectedText())),
                        "queued-message-" + request.requestId());
            }
            requests.markCompleted(request.requestId());
        } catch (RuntimeException exception) {
            String error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            log.warn("queued Agent request failed: requestId={}, attempt={}, error={}",
                    request.requestId(), request.attemptCount(), error);
            if (request.attemptCount() != null && request.attemptCount() < MAX_ATTEMPTS) {
                requests.requeue(request.requestId(), error);
            } else {
                requests.markFailed(request.requestId(), error);
            }
        }
    }
}
