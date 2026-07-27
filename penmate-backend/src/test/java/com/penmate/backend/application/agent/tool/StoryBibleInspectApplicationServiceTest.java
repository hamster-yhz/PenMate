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
    void overview_reports_facts_without_claiming_semantic_completeness() {
        StoryBible root = new StoryBible();
        root.setContentRevision(3L);
        StoryBibleNodeType coreType = type(11L, "STORY_CORE");
        StoryBibleNode core = node(21L, 11L, 1L, "{}", "", "");
        when(storyBible.get(7L)).thenReturn(root);
        when(storyBible.listNodeTypes(7L)).thenReturn(List.of(coreType));
        when(storyBible.listNodes(7L, null, null, null)).thenReturn(List.of(core));

        var result = service.execute(context(7L), request("{\"operation\":\"overview\"}"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(jsonCodec.readObject(result.toolOutput()))
                .containsEntry("activeNodeCount", 1)
                .containsKeys("storyCore", "missingRequiredStoryCoreFields", "structuralIssues", "latestChanges")
                .doesNotContainKeys("contentReady", "nextAction");
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

    @Test
    void types_treats_a_semantic_family_passed_as_type_code_as_a_recoverable_filter() {
        StoryBibleNodeType coreType = type(11L, "STORY_CORE");
        coreType.setSemanticFamily(StoryBibleSemanticFamily.CORE);
        when(storyBible.listNodeTypes(7L)).thenReturn(List.of(coreType));
        when(storyBible.listNodes(7L, 11L, null, null)).thenReturn(List.of());
        when(storyBible.listCategories(7L)).thenReturn(List.of());
        when(storyBible.listTags(7L)).thenReturn(List.of());

        var result = service.execute(context(7L), request("{\"operation\":\"types\",\"typeCode\":\"CORE\"}"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        Map<String, Object> output = jsonCodec.readObject(result.toolOutput());
        assertThat(output).containsEntry("resolvedFilter", "semanticFamily");
        assertThat(String.valueOf(output.get("warning"))).contains("semantic family");
        assertThat((List<?>) output.get("types")).singleElement().satisfies(value ->
                assertThat(((Map<?, ?>) value).get("typeCode")).isEqualTo("STORY_CORE"));
    }

    @Test
    void unknown_type_filter_returns_available_codes_instead_of_failing() {
        StoryBibleNodeType location = type(12L, "LOCATION");
        location.setSemanticFamily(StoryBibleSemanticFamily.WORLD);
        when(storyBible.listNodeTypes(7L)).thenReturn(List.of(location));
        when(storyBible.listCategories(7L)).thenReturn(List.of());
        when(storyBible.listTags(7L)).thenReturn(List.of());

        var result = service.execute(context(7L),
                request("{\"operation\":\"types\",\"typeCode\":\"WORLD_RULE\"}"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        Map<String, Object> output = jsonCodec.readObject(result.toolOutput());
        assertThat((List<?>) output.get("types")).isEmpty();
        assertThat(String.valueOf(output.get("warning"))).contains("Unknown typeCode", "WORLD_RULE");
        assertThat((List<?>) output.get("availableTypes")).singleElement().satisfies(value ->
                assertThat(((Map<?, ?>) value).get("typeCode")).isEqualTo("LOCATION"));
    }

    @Test
    void types_ignores_provider_placeholder_when_semantic_family_is_explicit() {
        StoryBibleNodeType coreType = type(11L, "STORY_CORE");
        coreType.setSemanticFamily(StoryBibleSemanticFamily.CORE);
        when(storyBible.listNodeTypes(7L)).thenReturn(List.of(coreType));
        when(storyBible.listNodes(7L, 11L, null, null)).thenReturn(List.of());
        when(storyBible.listCategories(7L)).thenReturn(List.of());
        when(storyBible.listTags(7L)).thenReturn(List.of());

        var result = service.execute(context(7L), request("""
                {"operation":"types","typeCode":"placeholder","semanticFamily":"CORE"}
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        Map<String, Object> output = jsonCodec.readObject(result.toolOutput());
        assertThat(output).containsEntry("resolvedFilter", "semanticFamily");
        assertThat((List<?>) output.get("types")).hasSize(1);
        assertThat(String.valueOf(output.get("warning"))).contains("ignored");
    }

    @Test
    void nodes_returns_stable_exact_ids_revisions_and_pagination() {
        StoryBibleNodeType character = type(12L, "CHARACTER");
        StoryBibleNode mira = node(31L, 12L, 6L, "{}", "Pilot", "");
        mira.setTitle("Mira");
        StoryBibleNode nox = node(32L, 12L, 4L, "{}", "Navigator", "");
        nox.setTitle("Nox");
        when(storyBible.listNodeTypes(7L)).thenReturn(List.of(character));
        when(storyBible.listNodes(7L, null, StoryBibleCanonStatus.CANON, null))
                .thenReturn(List.of(nox, mira));

        var result = service.execute(context(7L), request("""
                {"operation":"nodes","canonStatus":"CANON","offset":0,"limit":1}
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        Map<String, Object> output = jsonCodec.readObject(result.toolOutput());
        assertThat(output).containsEntry("total", 2).containsEntry("hasMore", true)
                .containsEntry("nextOffset", 1);
        assertThat((List<?>) output.get("items")).singleElement().satisfies(value -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) (Map<?, ?>) value;
            assertThat(item).containsEntry("nodeId", "31")
                    .containsEntry("revision", 6).containsEntry("typeCode", "CHARACTER");
        });
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
