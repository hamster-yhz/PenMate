package com.penmate.backend.application.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoryBibleInspectApplicationServiceTest {

    private final StoryBibleApplicationService storyBible = mock(StoryBibleApplicationService.class);
    private final JacksonJsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
    private final StoryBibleInspectApplicationService service = new StoryBibleInspectApplicationService(storyBible, jsonCodec);

    @Test
    void reports_blank_bootstrap_as_not_content_ready() {
        StoryBible root = new StoryBible();
        root.setContentRevision(3L);
        StoryBibleNodeType coreType = type(11L, "STORY_CORE");
        StoryBibleNode core = node(21L, 11L, 1L, "{}", "", "");
        when(storyBible.get(7L)).thenReturn(root);
        when(storyBible.listNodeTypes(7L)).thenReturn(List.of(coreType));
        when(storyBible.listNodes(7L, null, null, null)).thenReturn(List.of(core));

        var result = service.execute(context(7L), request("{\"operation\":\"readiness\"}"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(jsonCodec.readObject(result.toolOutput()))
                .containsEntry("contentReady", false)
                .containsEntry("nextAction", "analyze_sources_and_present_initialization_plan_for_user_confirmation");
    }

    @Test
    void exact_node_inspection_returns_revision_schema_and_structured_attributes() {
        StoryBibleNodeType characterType = type(12L, "CHARACTER");
        StoryBibleNode character = node(31L, 12L, 6L,
                "{\"coreMotivation\":\"protect the city\"}", "Guardian", "Details");
        when(storyBible.getNodeDetails(7L, 31L)).thenReturn(
                new StoryBibleApplicationService.NodeDetails(character, List.of(), List.of(), List.of()));
        when(storyBible.listNodeTypes(7L)).thenReturn(List.of(characterType));
        when(storyBible.listRelations(7L, List.of(31L))).thenReturn(List.of());
        when(storyBible.listProgressions(7L, List.of(31L))).thenReturn(List.of());

        var result = service.execute(context(7L), request("{\"operation\":\"node\",\"nodeId\":31}"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        Map<String, Object> output = jsonCodec.readObject(result.toolOutput());
        assertThat(output).containsEntry("nodeId", "31")
                .containsEntry("revision", 6)
                .containsEntry("typeCode", "CHARACTER");
        assertThat(((Map<?, ?>) output.get("attributes")).get("coreMotivation"))
                .isEqualTo("protect the city");
        assertThat(output.get("fieldSchema")).isInstanceOf(Map.class);
    }

    private StoryBibleNodeType type(Long typeId, String code) {
        StoryBibleNodeType type = new StoryBibleNodeType();
        type.setTypeId(typeId);
        type.setTypeCode(code);
        type.setSemanticFamily(StoryBibleSemanticFamily.CHARACTER);
        type.setDisplayName(code);
        type.setFieldSchemaJson("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}");
        type.setSystem(true);
        type.setSortOrder(10);
        return type;
    }

    private StoryBibleNode node(Long nodeId, Long typeId, Long revision, String attributes,
                                String summary, String body) {
        StoryBibleNode node = new StoryBibleNode();
        node.setNodeId(nodeId);
        node.setTypeId(typeId);
        node.setRevision(revision);
        node.setTitle("Node");
        node.setSummary(summary);
        node.setBodyMarkdown(body);
        node.setAttributesJson(attributes);
        node.setCanonStatus(StoryBibleCanonStatus.CANON);
        node.setInclusionPolicy(StoryBibleInclusionPolicy.AUTO_RETRIEVE);
        return node;
    }

    private AuthorizedAgentRunContext context(Long projectId) {
        AuthorizedAgentRunContext context = mock(AuthorizedAgentRunContext.class);
        when(context.projectId()).thenReturn(projectId);
        when(context.input()).thenReturn(mock(com.penmate.backend.domain.agent.run.model.AgentRunInput.class));
        return context;
    }

    private ToolCallRequest request(String args) {
        return new ToolCallRequest(1L, "story_bible_inspect", args, "idem", "call", 9L);
    }
}
