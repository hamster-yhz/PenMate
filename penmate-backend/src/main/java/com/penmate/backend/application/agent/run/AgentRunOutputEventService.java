package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentEvent;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentRunOutputEventService {

    private final AgentPartialMessageCheckpointStore checkpoints;
    private final AgentRunEventPublisher events;

    public AgentRunOutputEventService(AgentPartialMessageCheckpointStore checkpoints,
                                      AgentRunEventPublisher events) {
        this.checkpoints = checkpoints;
        this.events = events;
    }

    public AgentEvent persistInterrupted(Long runId) {
        return persistInterrupted(runId, null);
    }

    public AgentEvent persistInterrupted(Long runId, String preferredText) {
        AgentPartialMessageCheckpointStore.Snapshot checkpoint = checkpoints.find(runId).orElse(null);
        String text = preferredText == null || preferredText.isBlank()
                ? checkpoint == null ? "" : checkpoint.text()
                : preferredText;
        if (text.isBlank()) return null;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", "assistant");
        payload.put("text", text);
        payload.put("offset", (long) text.length());
        if (checkpoint != null) payload.put("updatedAt", checkpoint.updatedAt().toString());
        return events.publish(runId, "message.interrupted", payload);
    }
}
