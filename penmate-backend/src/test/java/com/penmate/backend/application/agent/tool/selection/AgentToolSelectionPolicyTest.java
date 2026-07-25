package com.penmate.backend.application.agent.tool.selection;

import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentToolSelectionPolicyTest {

    @Test
    void selects_only_tools_supported_by_the_execution_profile() {
        AgentToolSelectionPolicy policy = policy(
                definition("chapter_edit", ToolLifecycleStatus.ACTIVE, Set.of("default", "rewrite")),
                definition("story_bible_update", ToolLifecycleStatus.ACTIVE, Set.of("default", "world-build")),
                definition("book_crud", ToolLifecycleStatus.DRAINING, Set.of("*")));

        assertThat(policy.select(profile("rewrite", List.of())))
                .extracting(schema -> schema.toolCode())
                .containsExactly("chapter_edit");
        assertThat(policy.select(profile("world-build", List.of())))
                .extracting(schema -> schema.toolCode())
                .containsExactly("story_bible_update");
    }

    @Test
    void honors_an_explicit_task_profile_allowlist_in_declared_order() {
        AgentToolSelectionPolicy policy = policy(
                definition("rag_query", ToolLifecycleStatus.ACTIVE, Set.of("*")),
                definition("quality_review", ToolLifecycleStatus.ACTIVE, Set.of("*")));

        assertThat(policy.select(profile("default", List.of("quality_review", "rag_query"))))
                .extracting(schema -> schema.toolCode())
                .containsExactly("quality_review", "rag_query");
    }

    @Test
    void rejects_non_active_or_profile_incompatible_requested_tools() {
        AgentToolSelectionPolicy policy = policy(
                definition("chapter_edit", ToolLifecycleStatus.ACTIVE, Set.of("rewrite")),
                definition("book_crud", ToolLifecycleStatus.DRAINING, Set.of("*")),
                definition("retired_tool", ToolLifecycleStatus.DISABLED, Set.of("*")));

        assertThatThrownBy(() -> policy.select(profile("default", List.of("book_crud"))))
                .hasMessage("Task profile requests an unavailable tool: book_crud");
        assertThatThrownBy(() -> policy.select(profile("default", List.of("chapter_edit"))))
                .hasMessage("Task profile requests an unavailable tool: chapter_edit");
        assertThatThrownBy(() -> policy.select(profile("default", List.of("retired_tool"))))
                .hasMessage("Task profile requests an unavailable tool: retired_tool");
    }

    private AgentToolSelectionPolicy policy(AgentToolDefinition... definitions) {
        return new AgentToolSelectionPolicy(new InMemoryAgentToolDefinitionSource(List.of(definitions)));
    }

    private AgentToolDefinition definition(String toolCode, ToolLifecycleStatus status, Set<String> profiles) {
        return () -> new AgentToolDescriptor(
                toolCode,
                new ToolPresentation(toolCode),
                new ToolExposure(status, toolCode, "{\"type\":\"object\"}", profiles),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 0, Map.of()));
    }

    private TaskProfile profile(String executionProfile, List<String> tools) {
        return new TaskProfile(
                List.of(), executionProfile, tools, List.of(), null,
                false, true, false, "test");
    }
}
