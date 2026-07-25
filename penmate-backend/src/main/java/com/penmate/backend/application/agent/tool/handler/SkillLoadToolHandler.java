package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class SkillLoadToolHandler implements AgentToolHandler {

    private final AgentSkillActivationService skillActivationService;
    private final JsonCodec jsonCodec;

    public SkillLoadToolHandler(AgentSkillActivationService skillActivationService, JsonCodec jsonCodec) {
        this.skillActivationService = Objects.requireNonNull(skillActivationService, "skillActivationService");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec");
    }

    @Override
    public String toolCode() {
        return "skill_load";
    }

    @Override
    public void validate(AuthorizedAgentRunContext context, ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        Map<String, Object> args = jsonCodec.readObject(request.toolArgsJson());
        String skill = JsonValues.string(args, "skill");
        if (skill == null || skill.isBlank()) {
            throw new IllegalArgumentException("skill is required");
        }
    }

    @Override
    public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        try {
            Map<String, Object> args = jsonCodec.readObject(request.toolArgsJson());
            String skill = JsonValues.string(args, "skill").trim();
            var activation = skillActivationService.activateAutomatically(
                    context.runId(), skill, request.toolCallId());
            var binding = activation.binding();
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("requestedSkill", skill);
            output.put("skill", binding.skillName());
            output.put("contentHash", binding.contentHash());
            output.put("status", activation.status());
            if (!"ALREADY_ACTIVE".equals(activation.status())) {
                output.put("path", activation.path());
                output.put("content", binding.content());
            }
            return ToolCallResult.success(jsonCodec.write(output));
        } catch (AgentSkillActivationService.SkillActivationFailure ex) {
            return ToolCallResult.failed(ex.errorCode(), ex.getMessage());
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "skill load failed"
                    : ex.getMessage();
            return ToolCallResult.failed("SKILL_LOAD_FAILED", message);
        }
    }
}
