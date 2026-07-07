package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class SkillPromptReadToolHandler implements AgentToolHandler {

    private final SkillPromptRegistry skillPromptRegistry;

    public SkillPromptReadToolHandler(SkillPromptRegistry skillPromptRegistry) {
        this.skillPromptRegistry = Objects.requireNonNull(skillPromptRegistry, "skillPromptRegistry");
    }

    @Override
    public String toolCode() {
        return "skill_prompt_read";
    }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        JSONObject args = AgentJsonCodec.parseObj(request.toolArgsJson());
        String skill = AgentJsonCodec.getString(args, "skill");
        if (skill == null || skill.isBlank()) {
            throw new IllegalArgumentException("skill is required");
        }
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        try {
            JSONObject args = AgentJsonCodec.parseObj(request.toolArgsJson());
            String skill = AgentJsonCodec.getString(args, "skill").trim();
            SystemPromptDocument document = skillPromptRegistry.load(skill);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("skill", skill);
            output.put("path", document == null ? "" : document.path());
            output.put("content", document == null ? "" : document.content());
            return ToolCallResult.success(AgentJsonCodec.toJson(output));
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "skill prompt read failed"
                    : ex.getMessage();
            return ToolCallResult.failed("SKILL_PROMPT_READ_FAILED", message);
        }
    }
}
