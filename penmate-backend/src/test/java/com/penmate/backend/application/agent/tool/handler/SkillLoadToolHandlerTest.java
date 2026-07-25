package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.skill.AgentRunSkillBinding;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillLoadToolHandlerTest {

    private final AgentSkillActivationService skillActivationService = mock(AgentSkillActivationService.class);
    private final SkillLoadToolHandler handler = new SkillLoadToolHandler(
            skillActivationService, new JacksonJsonCodec(new ObjectMapper()));

    @Test
    void should_load_full_skill_prompt_content_from_registry() {
        when(skillActivationService.activateAutomatically(8001L, "writer", null)).thenReturn(
                new AgentSkillActivationService.AutoActivation(
                        "ACTIVATED",
                        new AgentRunSkillBinding(8001L, "writer", "abc123", "AUTO", null,
                                "Full writer skill prompt", null),
                        "prompts/agent/system/skills/writer/SKILL.md"));

        ToolCallResult result = handler.execute(request("{\"skill\":\"writer\"}"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getStr("requestedSkill")).isEqualTo("writer");
        assertThat(output.getStr("skill")).isEqualTo("writer");
        assertThat(output.getStr("contentHash")).isEqualTo("abc123");
        assertThat(output.getStr("path")).isEqualTo("prompts/agent/system/skills/writer/SKILL.md");
        assertThat(output.getStr("content")).isEqualTo("Full writer skill prompt");
    }

    @Test
    void should_fail_when_skill_does_not_exist() {
        when(skillActivationService.activateAutomatically(8001L, "missing", null))
                .thenThrow(new AgentSkillActivationService.SkillActivationFailure(
                        "SKILL_NOT_FOUND", "Skill not found: missing"));

        ToolCallResult result = handler.execute(request("{\"skill\":\"missing\"}"));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("SKILL_NOT_FOUND");
        assertThat(result.errorMessage()).isEqualTo("Skill not found: missing");
    }

    @Test
    void should_reject_missing_skill_argument() {
        assertThatThrownBy(() -> handler.validate(request("{}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("skill is required");
    }

    private ToolCallRequest request(String toolArgsJson) {
        return new ToolCallRequest(
                9001L,
                8001L,
                7001L,
                "skill_load",
                toolArgsJson,
                1001L,
                "trace-1",
                "{}",
                "idem-1"
        );
    }
}
