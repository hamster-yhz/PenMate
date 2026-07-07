package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillPromptReadToolHandlerTest {

    private final SkillPromptRegistry skillPromptRegistry = mock(SkillPromptRegistry.class);
    private final SkillPromptReadToolHandler handler = new SkillPromptReadToolHandler(skillPromptRegistry);

    @Test
    void should_read_full_skill_prompt_content_from_registry() {
        when(skillPromptRegistry.load("writer")).thenReturn(new SystemPromptDocument(
                "00-base-role.md",
                "prompts/agent/system/skills/writer/00-base-role.md",
                "Full writer skill prompt"
        ));

        ToolCallResult result = handler.execute(request("{\"skill\":\"writer\"}"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getStr("skill")).isEqualTo("writer");
        assertThat(output.getStr("path")).isEqualTo("prompts/agent/system/skills/writer/00-base-role.md");
        assertThat(output.getStr("content")).isEqualTo("Full writer skill prompt");
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
                "skill_prompt_read",
                toolArgsJson,
                1001L,
                "trace-1",
                "{}",
                "idem-1"
        );
    }
}
