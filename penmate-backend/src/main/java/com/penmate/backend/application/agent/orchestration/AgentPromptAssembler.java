package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.application.agent.prompt.StructuredPromptBlockFormatter;
import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@Component
public class AgentPromptAssembler {

    private final SystemPromptProvider systemPromptProvider;
    private final StructuredPromptBlockFormatter structuredPromptBlockFormatter;

    public AgentPromptAssembler(SystemPromptProvider systemPromptProvider,
                                StructuredPromptBlockFormatter structuredPromptBlockFormatter) {
        this.systemPromptProvider = systemPromptProvider;
        this.structuredPromptBlockFormatter = structuredPromptBlockFormatter;
    }

    public List<Map<String, Object>> buildInitialMessages(AgentGenerationTask task,
                                                          AgentTaskContext taskContext,
                                                          List<RagRetrievedChunk> ragChunks) {
        return toWireMessages(buildExecutionMessages(task, taskContext, ragChunks, resolveProfile(task), "", List.of()));
    }

    public List<Map<String, Object>> buildExecutionMessages(AgentGenerationTask task,
                                                            AgentTaskContext taskContext,
                                                            List<RagRetrievedChunk> ragChunks,
                                                            String executionProfile,
                                                            String storyBibleContent) {
        return toWireMessages(buildExecutionMessages(task, taskContext, ragChunks, executionProfile, storyBibleContent, List.of()));
    }

    public List<AgentLlmMessage> buildExecutionMessages(AgentGenerationTask task,
                                                        AgentTaskContext taskContext,
                                                        List<RagRetrievedChunk> ragChunks,
                                                        String executionProfile,
                                                        String storyBibleContent,
                                                        List<AgentLlmMessage> conversationWindow) {
        String prompt = task.getPromptSnapshot() == null ? "" : task.getPromptSnapshot().trim();
        String style = taskContext == null || taskContext.getStyleSnapshotJson() == null
                ? ""
                : taskContext.getStyleSnapshotJson().trim();
        String storyBible = storyBibleContent == null ? "" : storyBibleContent.trim();
        String contextSystemMessage = buildExecutionContextSystemMessage(
                style,
                storyBible.isEmpty() ? List.of() : List.of(storyBible),
                List.of(),
                List.of(),
                formatTaskPathRagRefs(ragChunks)
        );
        String userRequestBlock = structuredPromptBlockFormatter.wrapBlock("user_request", prompt);

        String profile = executionProfile == null || executionProfile.isBlank() ? resolveProfile(task) : executionProfile.trim();
        SystemPromptBundle promptBundle = systemPromptProvider.loadBundle("execution", profile);
        List<AgentLlmMessage> result = new ArrayList<>();
        result.add(AgentLlmMessage.system(promptBundle.assembledPrompt()));
        if (!contextSystemMessage.isBlank()) {
            result.add(AgentLlmMessage.system(contextSystemMessage));
        }
        if (conversationWindow != null && !conversationWindow.isEmpty()) {
            result.addAll(conversationWindow);
        }
        result.add(AgentLlmMessage.user(userRequestBlock));
        return List.copyOf(result);
    }

    public List<Map<String, Object>> buildExecutionMessages(PromptPlan promptPlan,
                                                            ContextPackage contextPackage,
                                                            String userRequest) {
        return toWireMessages(buildExecutionMessages(promptPlan, contextPackage, userRequest, List.of()));
    }

    public List<AgentLlmMessage> buildExecutionMessages(PromptPlan promptPlan,
                                                        ContextPackage contextPackage,
                                                        String userRequest,
                                                        List<AgentLlmMessage> conversationWindow) {
        ContextPackage resolvedContext = Objects.requireNonNull(contextPackage, "contextPackage");
        String contextSystemMessage = buildExecutionContextSystemMessage(
                resolvedContext.styleSnapshot(),
                resolvedContext.storyBibleEntries(),
                resolvedContext.conflicts(),
                resolvedContext.missingContextFlags(),
                resolvedContext.ragRefs()
        );
        String userRequestBlock = structuredPromptBlockFormatter.wrapBlock("user_request", userRequest == null ? "" : userRequest.trim());

        List<AgentLlmMessage> result = new ArrayList<>();
        result.add(AgentLlmMessage.system(promptPlan == null ? "" : promptPlan.assembledPromptPreview()));
        if (!contextSystemMessage.isBlank()) {
            result.add(AgentLlmMessage.system(contextSystemMessage));
        }
        if (conversationWindow != null && !conversationWindow.isEmpty()) {
            result.addAll(conversationWindow);
        }
        result.add(AgentLlmMessage.user(userRequestBlock));
        return List.copyOf(result);
    }

    private String buildExecutionContextSystemMessage(String style,
                                                      List<String> storyBibleEntries,
                                                      List<String> conflicts,
                                                      List<String> missingFlags,
                                                      List<String> ragRefs) {
        StringJoiner contextBuilder = new StringJoiner("\n\n");
        if (style != null && !style.isBlank()) {
            contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"style\"", style));
        }
        if (storyBibleEntries != null && !storyBibleEntries.isEmpty()) {
            contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"story_bible\"", String.join("\n", storyBibleEntries)));
        }
        if (conflicts != null && !conflicts.isEmpty()) {
            contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"conflict\"", String.join("\n", conflicts)));
        }
        if (missingFlags != null && !missingFlags.isEmpty()) {
            contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"missing\"", String.join("\n", missingFlags)));
        }
        if (ragRefs != null && !ragRefs.isEmpty()) {
            contextBuilder.add(structuredPromptBlockFormatter.wrapBlock("context type=\"rag\"", String.join("\n", ragRefs)));
        }
        return contextBuilder.toString();
    }

    private List<String> formatTaskPathRagRefs(List<RagRetrievedChunk> ragChunks) {
        if (ragChunks == null || ragChunks.isEmpty()) {
            return List.of();
        }
        List<String> ragRefs = new ArrayList<>();
        for (RagRetrievedChunk chunk : ragChunks) {
            ragRefs.add("- ["
                    + (chunk.getDocumentTitle() == null ? "document" : chunk.getDocumentTitle())
                    + "#"
                    + (chunk.getChunkNo() == null ? 0 : chunk.getChunkNo())
                    + "] "
                    + (chunk.getContentText() == null ? "" : chunk.getContentText()));
        }
        return List.copyOf(ragRefs);
    }

    private List<Map<String, Object>> toWireMessages(List<AgentLlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .map(this::toWireMessage)
                .toList();
    }

    private Map<String, Object> toWireMessage(AgentLlmMessage message) {
        Map<String, Object> wireMessage = new LinkedHashMap<>();
        wireMessage.put("role", message.role().wireValue());
        wireMessage.put("content", message.content());
        if (message.toolCallId() != null) {
            wireMessage.put("tool_call_id", message.toolCallId());
        }
        if (!message.toolCalls().isEmpty()) {
            wireMessage.put("tool_calls", message.toolCalls().stream()
                    .map(this::toWireToolCall)
                    .toList());
        }
        return wireMessage;
    }

    private Map<String, Object> toWireToolCall(AgentLlmToolCallPayload toolCall) {
        Map<String, Object> wireToolCall = new LinkedHashMap<>();
        wireToolCall.put("id", toolCall.id());
        wireToolCall.put("type", toolCall.type());
        wireToolCall.put("function", Map.of(
                "name", toolCall.functionName(),
                "arguments", toolCall.argumentsJson()
        ));
        return wireToolCall;
    }

    private String resolveProfile(AgentGenerationTask task) {
        if (task == null || task.getTaskType() == null) {
            return "default";
        }
        return switch (task.getTaskType().trim().toUpperCase()) {
            case "WORLD_BUILD" -> "world-build";
            case "REWRITE" -> "rewrite";
            default -> "default";
        };
    }
}
