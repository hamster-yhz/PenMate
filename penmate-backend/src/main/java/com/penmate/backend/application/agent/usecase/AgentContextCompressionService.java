package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationService;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentSessionContextSummary;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionContextSummaryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentContextCompressionService {
    private static final String SYSTEM_PROMPT = """
            You compress a PenMate Agent conversation for future continuation by the same model.
            Preserve user intent, decisions, constraints, completed work, unresolved work, named entities,
            concrete identifiers, important tool outcomes, and errors. Do not invent facts.
            Return one JSON object only with this schema:
            {"summary":"string","decisions":["string"],"completed":["string"],
             "pending":["string"],"constraints":["string"],"importantFacts":["string"]}
            """;

    private final AgentRepository messages;
    private final AgentSessionContextSummaryRepository summaries;
    private final AgentModelRoutingService modelRouting;
    private final AgentLlmInvocationService invocations;
    private final JsonCodec json;

    public AgentContextCompressionService(AgentRepository messages,
                                          AgentSessionContextSummaryRepository summaries,
                                          AgentModelRoutingService modelRouting,
                                          AgentLlmInvocationService invocations,
                                          JsonCodec json) {
        this.messages = messages;
        this.summaries = summaries;
        this.modelRouting = modelRouting;
        this.invocations = invocations;
        this.json = json;
    }

    public AgentSessionContextSummary compress(Long projectId, Long sessionId, Long ownerUserId, String traceId) {
        AgentSessionContextSummary previous = summaries.find(sessionId);
        int previousCutoff = previous == null || previous.cutoffMessageSeq() == null ? 0 : previous.cutoffMessageSeq();
        List<AgentMessage> tail = messages.listMessages(sessionId).stream()
                .filter(message -> message != null && message.getSeqNo() != null && message.getSeqNo() > previousCutoff)
                .filter(message -> message.getContentMd() != null && !message.getContentMd().isBlank())
                .filter(message -> "user".equalsIgnoreCase(message.getRole()) || "assistant".equalsIgnoreCase(message.getRole()))
                .toList();
        if (tail.isEmpty()) throw BusinessException.badRequest("没有可压缩的上下文");

        List<Map<String, Object>> conversation = new ArrayList<>();
        for (AgentMessage message : tail) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", message.getSeqNo());
            item.put("role", message.getRole());
            item.put("content", message.getContentMd());
            conversation.add(item);
        }
        Map<String, Object> source = new LinkedHashMap<>();
        if (previous != null) source.put("previousSummary", json.readObject(previous.summaryJson()));
        source.put("messages", conversation);

        var config = modelRouting.resolveExecutionConfig(ownerUserId, null, traceId);
        var response = invocations.invokeBuffered(new AgentLlmTurnRequest(List.of(
                AgentLlmMessage.system(SYSTEM_PROMPT), AgentLlmMessage.user(json.write(source))
        ), List.of(), "none"), config);
        Map<String, Object> compacted;
        try {
            compacted = json.readObject(response.assistantText());
        } catch (RuntimeException exception) {
            throw BusinessException.of("上下文压缩失败：模型未返回有效 JSON");
        }
        Object summary = compacted.get("summary");
        if (!(summary instanceof String text) || text.isBlank()) {
            throw BusinessException.of("上下文压缩失败：摘要为空");
        }

        int cutoff = tail.stream().map(AgentMessage::getSeqNo).max(Integer::compareTo).orElse(previousCutoff);
        AgentSessionContextSummary saved = new AgentSessionContextSummary(
                sessionId, projectId, ownerUserId, json.writeCanonical(compacted), cutoff,
                response.tokenUsage().promptTokens(), response.tokenUsage().completionTokens(), Instant.now());
        if (summaries.upsert(saved) != 1) throw new IllegalStateException("failed to persist context summary");
        return saved;
    }
}
