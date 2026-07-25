package com.penmate.backend.application.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.application.storybible.command.StoryBibleCommands;
import com.penmate.backend.domain.storybible.model.StoryBibleActorType;
import com.penmate.backend.domain.storybible.model.StoryBibleAlias;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static com.penmate.backend.application.agent.tool.runtime.AgentToolTestContext.context;

@ExtendWith(MockitoExtension.class)
class DefaultStoryBibleUpdateApplicationServiceTest {

    @Mock
    private StoryBibleApplicationService storyBibleApplicationService;

    private ObjectMapper objectMapper;
    private DefaultStoryBibleUpdateApplicationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new DefaultStoryBibleUpdateApplicationService(
                storyBibleApplicationService, new JacksonJsonCodec(objectMapper));
    }

    @Test
    void should_execute_the_approved_batch_in_order_as_agent() throws Exception {
        StoryBibleTag created = new StoryBibleTag();
        created.setTagId(71L);
        when(storyBibleApplicationService.createTag(
                eq(41L), any(), eq(StoryBibleActorType.AGENT), eq(7L), eq(42L)))
                .thenReturn(created);

        ToolCallResult result = service.execute(context(41L, 42L, 43L, 44L, 7L, null, 1L, null, "trace-1"), request("""
                {"operation":"batch","operations":[
                  {"kind":"create_tag","name":"clue","color":"#123456"},
                  {"kind":"delete_tag","tagId":71}
                ]}
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(objectMapper.readTree(result.toolOutput()).path("appliedCount").asInt()).isEqualTo(2);
        assertThat(objectMapper.readTree(result.toolOutput()).path("results").get(0).path("entityId").asText())
                .isEqualTo("71");

        InOrder order = inOrder(storyBibleApplicationService);
        order.verify(storyBibleApplicationService).createTag(
                41L, new StoryBibleCommands.CreateTag("clue", "#123456"), StoryBibleActorType.AGENT, 7L, 42L);
        order.verify(storyBibleApplicationService).deleteTag(
                41L, 71L, StoryBibleActorType.AGENT, 7L, 42L);
    }

    @Test
    void should_preserve_unspecified_node_fields_and_require_revision() {
        StoryBibleNode existing = new StoryBibleNode();
        existing.setNodeId(81L);
        existing.setTypeId(91L);
        existing.setTitle("Mira");
        existing.setSummary("Pilot");
        existing.setBodyMarkdown("Old body");
        existing.setAttributesJson("{\"age\":24}");
        existing.setInclusionPolicy(StoryBibleInclusionPolicy.AUTO_RETRIEVE);
        existing.setCanonStatus(StoryBibleCanonStatus.CANON);
        existing.setRevision(3L);
        StoryBibleAlias alias = new StoryBibleAlias();
        alias.setAlias("Captain");
        when(storyBibleApplicationService.getNodeDetails(41L, 81L))
                .thenReturn(new StoryBibleApplicationService.NodeDetails(existing, List.of(alias), List.of(101L), List.of(102L)));
        StoryBibleNode updated = new StoryBibleNode();
        updated.setNodeId(81L);
        updated.setRevision(4L);
        when(storyBibleApplicationService.updateNode(eq(41L), eq(81L), any(),
                eq(StoryBibleActorType.AGENT), eq(7L), eq(42L))).thenReturn(updated);

        ToolCallResult result = service.execute(context(41L, 42L, 43L, 44L, 7L, null, 1L, null, "trace-1"), request("""
                {"operation":"batch","operations":[
                  {"kind":"update_node","nodeId":81,"expectedRevision":3,"summary":"New pilot"}
                ]}
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        ArgumentCaptor<StoryBibleCommands.UpdateNode> command = ArgumentCaptor.forClass(StoryBibleCommands.UpdateNode.class);
        verify(storyBibleApplicationService).updateNode(eq(41L), eq(81L), command.capture(),
                eq(StoryBibleActorType.AGENT), eq(7L), eq(42L));
        assertThat(command.getValue()).isEqualTo(new StoryBibleCommands.UpdateNode(
                3L, 91L, "Mira", "New pilot", "Old body", "{\"age\":24}",
                StoryBibleInclusionPolicy.AUTO_RETRIEVE, StoryBibleCanonStatus.CANON,
                List.of("Captain"), List.of(101L), List.of(102L)
        ));
    }

    @Test
    void should_reject_legacy_read_operation_without_touching_story_bible() {
        assertThatThrownBy(() -> service.execute(
                context(41L, 42L, 43L, 44L, 7L, null, 1L, null, "trace-1"),
                request("{\"operation\":\"list\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operation must be batch");
        verifyNoInteractions(storyBibleApplicationService);
    }

    @Test
    void should_stop_at_the_first_failed_mutation() {
        StoryBibleTag created = new StoryBibleTag();
        created.setTagId(71L);
        when(storyBibleApplicationService.createTag(
                eq(41L), any(), eq(StoryBibleActorType.AGENT), eq(7L), eq(42L))).thenReturn(created);
        org.mockito.Mockito.doThrow(new IllegalStateException("tag is in use"))
                .when(storyBibleApplicationService)
                .deleteTag(41L, 71L, StoryBibleActorType.AGENT, 7L, 42L);

        assertThatThrownBy(() -> service.execute(
                context(41L, 42L, 43L, 44L, 7L, null, 1L, null, "trace-1"), request("""
                {"operation":"batch","operations":[
                  {"kind":"create_tag","name":"clue"},
                  {"kind":"delete_tag","tagId":71},
                  {"kind":"create_category","name":"unused"}
                ]}
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operations[1] failed: tag is in use");

        verify(storyBibleApplicationService, never()).createCategory(any(), any(), any(), any(), any());
    }

    private ToolCallRequest request(String args) {
        return new ToolCallRequest(42L, "story_bible_update", args, "idem-1", 1,
                "call-1", "[]", "[]", null, "APPROVED", "approval-1", 1L);
    }
}
