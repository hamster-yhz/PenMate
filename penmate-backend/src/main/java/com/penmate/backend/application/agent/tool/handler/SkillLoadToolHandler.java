package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
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

    private final SkillPromptRegistry skillPromptRegistry;
    private final JsonCodec jsonCodec;

    public SkillLoadToolHandler(SkillPromptRegistry skillPromptRegistry, JsonCodec jsonCodec) {
        this.skillPromptRegistry = Objects.requireNonNull(skillPromptRegistry, "skillPromptRegistry");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec");
    }

    @Override
    public String toolCode() {
        return "skill_load";
    }

    @Override
    public void validate(ToolCallRequest request) {
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
    public ToolCallResult execute(ToolCallRequest request) {
        try {
            Map<String, Object> args = jsonCodec.readObject(request.toolArgsJson());
            String skill = JsonValues.string(args, "skill").trim();
            SystemPromptDocument document = skillPromptRegistry.load(skill);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("skill", skill);
            output.put("path", document == null ? "" : document.path());
            output.put("content", document == null ? "" : document.content());
            return ToolCallResult.success(jsonCodec.write(output));
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "skill load failed"
                    : ex.getMessage();
            return ToolCallResult.failed("SKILL_LOAD_FAILED", message);
        }
    }
}
