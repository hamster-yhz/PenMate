package com.penmate.backend.application.agent.orchestration;

import cn.hutool.json.JSONObject;

import java.util.regex.Pattern;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentTaskResult;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 结果消息落库器。
 * <p>负责把生成工作流产出的最终 assistant 文本转换为会话消息并写入仓储，同时刷新会话最后消息指针。</p>
 * <p>该类只处理“结果消息持久化”这一单一职责，不负责任务状态流转、事件发布或模型调用。</p>
 */
@Component
@RequiredArgsConstructor
public class AgentTaskResultRecorder {

    private static final Pattern TOOL_TRACE_LINE_SPLITTER = Pattern.compile("\\r?\\n+");

    private final AgentRepository agentRepository;
    private final BusinessIdGenerator businessIdGenerator;

    public void recordAssistantResult(AgentGenerationTask task, String generatedText) {
        recordAssistantResult(task, generatedText, null);
    }

    public void recordAssistantResult(AgentGenerationTask task, String generatedText, String toolTraceJson) {
        AgentMessage assistantMessage = new AgentMessage();
        assistantMessage.setMessageId(businessIdGenerator.nextId());
        assistantMessage.setConversationId(task.getConversationId());
        assistantMessage.setRole("assistant");
        assistantMessage.setUserMessageType("GENERATION_RESULT");
        assistantMessage.setContentMd(generatedText);
        assistantMessage.setAttachmentsJson("[]");
        assistantMessage.setToolCallsJson("[]");
        assistantMessage.setSeqNo(agentRepository.nextMessageSeq(task.getConversationId()));
        if (agentRepository.insertMessage(assistantMessage) != 1) {
            throw new IllegalStateException("Failed to insert assistant result message");
        }

        AgentTaskResult taskResult = new AgentTaskResult();
        taskResult.setResultId(businessIdGenerator.nextId());
        taskResult.setTaskId(task.getTaskId());
        taskResult.setResultStatus("SUCCEEDED");
        taskResult.setAssistantMessageId(assistantMessage.getMessageId());
        String normalizedToolTraceJson = normalizeToolTraceJson(toolTraceJson);
        taskResult.setOutputMarkdown(generatedText);
        taskResult.setOutputStructuredJson(extractStructuredOutputJson(normalizedToolTraceJson));
        taskResult.setToolTraceJson(normalizedToolTraceJson);
        taskResult.setDraftSummary(extractDraftSummary(normalizedToolTraceJson));
        taskResult.setQualityReportSummary(extractQualityReportSummary(normalizedToolTraceJson));
        taskResult.setTodoSummary(extractTodoSummary(normalizedToolTraceJson));
        taskResult.setStoryBibleProposalSummary(extractStoryBibleProposalSummary(normalizedToolTraceJson));
        if (agentRepository.insertTaskResult(taskResult) != 1) {
            throw new IllegalStateException("Failed to insert task result snapshot");
        }
        if (agentRepository.updateGenerationTaskResultLink(task.getProjectId(), task.getTaskId(), taskResult.getResultId()) != 1) {
            throw new IllegalStateException("Failed to link generation task result");
        }

        if (agentRepository.touchConversationLastMessage(task.getConversationId()) != 1) {
            throw new IllegalStateException("Failed to touch conversation last message");
        }
    }

    private String normalizeToolTraceJson(String toolTraceJson) {
        if (toolTraceJson == null || toolTraceJson.isBlank()) {
            return null;
        }
        return toolTraceJson.trim();
    }

    private String extractStructuredOutputJson(String toolTraceJson) {
        String normalized = normalizeToolTraceJson(toolTraceJson);
        if (normalized == null) {
            return null;
        }
        String[] fragments = TOOL_TRACE_LINE_SPLITTER.split(normalized);
        int nonBlankFragmentCount = 0;
        String lastStructured = null;
        for (String fragment : fragments) {
            String trimmed = fragment == null ? null : fragment.trim();
            if (trimmed == null || trimmed.isEmpty()) {
                continue;
            }
            nonBlankFragmentCount += 1;
            String candidate = extractStructuredJsonCandidate(trimmed);
            if (candidate != null) {
                lastStructured = candidate;
            }
        }
        if (nonBlankFragmentCount > 1) {
            return lastStructured;
        }
        return extractStructuredJsonCandidate(normalized);
    }

    private String extractStructuredJsonCandidate(String raw) {
        String normalized = normalizeToolTraceJson(raw);
        if (normalized == null) {
            return null;
        }
        try {
            JSONObject jsonObject = AgentJsonCodec.parseObj(normalized);
            if (isDraftStructuredOutput(jsonObject)
                    || isQualityReviewStructuredOutput(jsonObject)
                    || isStoryBibleApprovalStructuredOutput(jsonObject)) {
                return normalized;
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractDraftSummary(String toolTraceJson) {
        return extractStructuredSummary(toolTraceJson, this::isDraftStructuredOutput);
    }

    private String extractQualityReportSummary(String toolTraceJson) {
        return extractStructuredSummary(toolTraceJson, this::isQualityReviewStructuredOutput);
    }

    private String extractTodoSummary(String toolTraceJson) {
        return extractStructuredSummary(toolTraceJson, this::isTodoStructuredOutput);
    }

    private String extractStoryBibleProposalSummary(String toolTraceJson) {
        String proposalFragment = extractStructuredSummary(toolTraceJson, this::isStoryBibleProposalStructuredOutput);
        if (proposalFragment != null) {
            return proposalFragment;
        }
        return extractStructuredSummary(toolTraceJson, this::isStoryBibleApprovalStructuredOutput);
    }

    private String extractStructuredSummary(String toolTraceJson, java.util.function.Predicate<JSONObject> matcher) {
        String normalized = normalizeToolTraceJson(toolTraceJson);
        if (normalized == null) {
            return null;
        }
        String[] fragments = TOOL_TRACE_LINE_SPLITTER.split(normalized);
        for (String fragment : fragments) {
            String trimmed = fragment == null ? null : fragment.trim();
            if (trimmed == null || trimmed.isEmpty()) {
                continue;
            }
            try {
                JSONObject jsonObject = AgentJsonCodec.parseObj(trimmed);
                if (matcher.test(jsonObject)) {
                    return trimmed;
                }
            } catch (Exception ex) {
                // ignore malformed fragment and continue scanning aggregated tool traces
            }
        }
        try {
            JSONObject jsonObject = AgentJsonCodec.parseObj(normalized);
            return matcher.test(jsonObject) ? normalized : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isDraftStructuredOutput(JSONObject jsonObject) {
        return jsonObject.containsKey("draftText")
                && jsonObject.containsKey("operation")
                && jsonObject.containsKey("preservedConstraints")
                && jsonObject.containsKey("sourceSummary");
    }

    private boolean isQualityReviewStructuredOutput(JSONObject jsonObject) {
        return jsonObject.containsKey("score")
                && jsonObject.containsKey("passes")
                && jsonObject.containsKey("issues")
                && jsonObject.containsKey("needsRevision")
                && jsonObject.containsKey("riskFlags")
                && jsonObject.containsKey("revisionSuggestions")
                && jsonObject.containsKey("currentRevisionRound")
                && jsonObject.containsKey("maxRevisionRounds")
                && jsonObject.containsKey("revisionAllowed")
                && jsonObject.containsKey("reviewSummary");
    }

    private boolean isTodoStructuredOutput(JSONObject jsonObject) {
        return jsonObject.containsKey("planTitle")
                && jsonObject.containsKey("planSummary")
                && jsonObject.containsKey("recommendedNextAction")
                && jsonObject.containsKey("items");
    }

    private boolean isStoryBibleProposalStructuredOutput(JSONObject jsonObject) {
        return jsonObject.containsKey("proposalSummary")
                && jsonObject.containsKey("items");
    }

    private boolean isStoryBibleApprovalStructuredOutput(JSONObject jsonObject) {
        return jsonObject.containsKey("approvalType")
                && jsonObject.containsKey("proposalSummary")
                && jsonObject.containsKey("entryKeys")
                && jsonObject.containsKey("nextAction");
    }
}
