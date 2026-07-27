package com.penmate.backend.application.agent.tool.selection;

import com.penmate.backend.application.agent.tool.definition.AgentToolDefinition;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.InMemoryAgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.ToolExposure;
import com.penmate.backend.application.agent.tool.definition.ToolGovernancePolicy;
import com.penmate.backend.application.agent.tool.definition.ToolLifecycleStatus;
import com.penmate.backend.application.agent.tool.definition.ToolPresentation;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolSelectionPolicyTest {

    @Test
    void exposes_every_active_tool_in_stable_order() {
        AgentToolSelectionPolicy policy = policy(
                definition("story_bible_node_write", ToolLifecycleStatus.ACTIVE),
                definition("chapter_patch", ToolLifecycleStatus.ACTIVE),
                definition("book_crud", ToolLifecycleStatus.DRAINING),
                definition("todo_crud", ToolLifecycleStatus.DISABLED));

        assertThat(policy.select())
                .extracting(schema -> schema.toolCode())
                .containsExactly("chapter_patch", "story_bible_node_write");
    }

    private AgentToolSelectionPolicy policy(AgentToolDefinition... definitions) {
        return new AgentToolSelectionPolicy(new InMemoryAgentToolDefinitionSource(List.of(definitions)));
    }

    private AgentToolDefinition definition(String toolCode, ToolLifecycleStatus status) {
        return () -> new AgentToolDescriptor(
                toolCode,
                new ToolPresentation(toolCode),
                new ToolExposure(status, toolCode, "{\"type\":\"object\"}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 0, Map.of()));
    }
}
