package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.StoryBibleUpdateApplicationService;
import com.penmate.backend.application.agent.tool.definition.StoryBibleUpdateToolDefinition;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.penmate.backend.application.agent.tool.runtime.AgentToolTestContext.context;

class StoryBibleUpdateToolHandlerTest {

    @Test
    void should_require_one_approval_for_the_complete_batch() {
        var descriptor = new StoryBibleUpdateToolDefinition().descriptor();

        assertThat(descriptor.toolCode()).isEqualTo("story_bible_update");
        assertThat(descriptor.presentation().displayName()).isEqualTo("故事设定更新");
        assertThat(descriptor.governancePolicy().defaultDecision().approvalRequired()).isTrue();
        assertThat(descriptor.governancePolicy().defaultDecision().approvalType())
                .isEqualTo("STORY_BIBLE_BATCH_MUTATION");
        assertThat(descriptor.governancePolicy().operationPolicies()).containsOnlyKeys("batch");
        assertThat(descriptor.governancePolicy().operationPolicies().get("batch").decision().approvalRequired()).isTrue();

        assertThat(descriptor.exposure().parametersJsonSchema())
                .contains("\"const\": \"batch\"")
                .contains("\"operations\"")
                .contains("\"create_progression\"")
                .doesNotContain("\"list\"")
                .doesNotContain("\"entryId\"");
    }

    @Test
    void should_expose_stable_tool_code_and_delegate_execution() {
        StoryBibleUpdateApplicationService service = mock(StoryBibleUpdateApplicationService.class);
        StoryBibleUpdateToolHandler handler = new StoryBibleUpdateToolHandler(service);
        ToolCallRequest request = request("call-story-bible-delegate", """
                {"operation":"batch","operations":[
                  {"kind":"delete_node","nodeId":1,"expectedRevision":1}
                ]}
                """);
        ToolCallResult expected = ToolCallResult.success("{\"delegated\":true}");
        when(service.execute(context(), request)).thenReturn(expected);

        assertThat(handler.toolCode()).isEqualTo("story_bible_update");
        assertThat(handler.mutatesState(context(), request)).isTrue();
        assertThat(handler.execute(context(), request)).isSameAs(expected);
        verify(service).execute(context(), request);
    }

    private static ToolCallRequest request(String toolCallId, String toolArgsJson) {
        return new ToolCallRequest(8001L, "story_bible_update", toolArgsJson,
                toolCallId + "-8001", 0, toolCallId, "{}", "[]", "[]",
                "RESUME_LOOP", null, 1L);
    }
}
