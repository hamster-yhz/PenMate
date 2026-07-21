package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationService;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.support.DraftGenerationCommand;
import com.penmate.backend.application.agent.tool.support.DraftResultView;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draft generation tool 处理器。
 */
@Component
@Slf4j
public class DraftGenerationToolHandler implements AgentToolHandler {

    private final AgentModelRoutingService agentModelRoutingService;
    private final AgentLlmInvocationService llmInvocations;

    @Autowired
    public DraftGenerationToolHandler(AgentModelRoutingService agentModelRoutingService,
                                      AgentLlmInvocationService llmInvocations) {
        this.agentModelRoutingService = agentModelRoutingService;
        this.llmInvocations = llmInvocations;
    }

    public DraftGenerationToolHandler(AgentModelRoutingService agentModelRoutingService,
                                      AgentLlmGateway llmGateway) {
        this(agentModelRoutingService, new AgentLlmInvocationService(llmGateway));
    }

    @Override
    public String toolCode() {
        return "draft_generation";
    }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        DraftGenerationCommand command = parseCommand(request.toolArgsJson());
        validateCommand(command);
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        DraftGenerationCommand command;
        try {
            command = parseCommand(request.toolArgsJson());
            validateCommand(command);
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "draft generation execution failed"
                    : ex.getMessage();
            log.warn("draft_generation 参数非法: runId={}, traceId={}, message={}",
                    request == null ? null : request.runId(),
                    request == null ? null : request.traceId(),
                    message);
            return new ToolCallResult("FAILED", null, null, "DRAFT_GENERATION_FAILED", message);
        }

        try {
            AgentLlmExecutionConfig executionConfig = agentModelRoutingService.resolveExecutionConfig(
                    request.operatorId(),
                    null,
                    request.traceId()
            );
            String prompt = buildPrompt(command);
            AgentLlmTurnResponse response = llmInvocations.invokeBuffered(
                    new AgentLlmTurnRequest(List.of(AgentLlmMessage.user(prompt)), List.of(), "none"),
                    executionConfig
            );
            String draftText = response.assistantText();
            String normalizedDraftText = requireMeaningfulDraftText(draftText);
            DraftResultView resultView = new DraftResultView(
                    normalizedDraftText,
                    command.operation(),
                    command.preservedConstraints(),
                    command.sourceSummary()
            );
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("draftText", resultView.draftText());
            output.put("operation", resultView.operation());
            output.put("preservedConstraints", resultView.preservedConstraints());
            output.put("sourceSummary", resultView.sourceSummary());
            log.info("draft_generation 执行成功: operation={}, projectId={}, runId={}, traceId={}",
                    command.operation(), request.projectId(), request.runId(), request.traceId());
            return ToolCallResult.success(AgentJsonCodec.toJson(output));
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "draft generation execution failed"
                    : ex.getMessage();
            log.warn("draft_generation 执行失败: operation={}, projectId={}, runId={}, traceId={}, message={}",
                    command.operation(), request.projectId(), request.runId(), request.traceId(), errorMessage);
            return new ToolCallResult("FAILED", null, null, "DRAFT_GENERATION_FAILED", errorMessage);
        }
    }

    private DraftGenerationCommand parseCommand(String toolArgsJson) {
        try {
            JSONObject args = AgentJsonCodec.parseObj(toolArgsJson);
            JSONArray preservedConstraints = args.getJSONArray("preservedConstraints");
            List<String> constraints = preservedConstraints == null
                    ? List.of()
                    : preservedConstraints.toList(String.class);
            return new DraftGenerationCommand(
                    AgentJsonCodec.getString(args, "operation"),
                    AgentJsonCodec.getString(args, "prompt"),
                    AgentJsonCodec.getString(args, "sourceText"),
                    AgentJsonCodec.getString(args, "instruction"),
                    constraints,
                    AgentJsonCodec.getString(args, "sourceSummary")
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("toolArgsJson must be valid JSON", ex);
        }
    }

    private void validateCommand(DraftGenerationCommand command) {
        String operation = command.operation();
        if (!"generate".equals(operation) && !"rewrite".equals(operation) && !"revise".equals(operation)) {
            throw new IllegalArgumentException("operation must be one of [generate, rewrite, revise]");
        }
        if ("generate".equals(operation) && command.prompt().isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (("rewrite".equals(operation) || "revise".equals(operation)) && command.sourceText().isBlank()) {
            throw new IllegalArgumentException("sourceText must not be blank");
        }
        if (("rewrite".equals(operation) || "revise".equals(operation)) && command.instruction().isBlank()) {
            throw new IllegalArgumentException("instruction must not be blank");
        }
    }

    private String requireMeaningfulDraftText(String draftText) {
        if (draftText == null || draftText.isBlank()) {
            throw new IllegalStateException("draft text must not be blank");
        }
        String normalized = draftText.trim();
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if ("ok".equals(lower) || "okay".equals(lower)) {
            throw new IllegalStateException("draft text must not be placeholder ok");
        }
        return normalized;
    }

    private String buildPrompt(DraftGenerationCommand command) {
        StringBuilder builder = new StringBuilder();
        switch (command.operation()) {
            case "generate" -> builder.append("请根据以下要求生成正文初稿。\n");
            case "rewrite" -> builder.append("请根据以下要求改写正文。\n");
            case "revise" -> builder.append("请根据以下修订要求套用修订。\n");
            default -> {
            }
        }
        if (!command.prompt().isBlank()) {
            builder.append("用户要求：").append(command.prompt()).append("\n");
        }
        if (!command.sourceText().isBlank()) {
            builder.append("原文内容：").append(command.sourceText()).append("\n");
        }
        if (!command.instruction().isBlank()) {
            builder.append("处理要求：").append(command.instruction()).append("\n");
        }
        if (!command.preservedConstraints().isEmpty()) {
            builder.append("保留约束：\n");
            for (String constraint : command.preservedConstraints()) {
                builder.append("- ").append(constraint == null ? "" : constraint).append("\n");
            }
        }
        if (!command.sourceSummary().isBlank()) {
            builder.append("来源摘要：").append(command.sourceSummary()).append("\n");
        }
        return builder.toString().trim();
    }
}
