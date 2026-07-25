package com.penmate.backend.application.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.context.AgentContextEpochService;
import com.penmate.backend.application.agent.context.AgentWorkingSetPromotionService;
import com.penmate.backend.application.agent.context.ContextEpochSnapshotCodec;
import com.penmate.backend.application.agent.context.StoryBibleContextResolver;
import com.penmate.backend.application.agent.context.StoryBibleRouteDecision;
import com.penmate.backend.application.agent.context.StoryBibleRouteRequest;
import com.penmate.backend.application.agent.context.StoryBibleRoutingMode;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.storybible.model.StoryBibleRelation;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryBibleSearchApplicationServiceTest {

    @Mock private AgentRunRepository runs;
    @Mock private AgentContextEpochService epochs;
    @Mock private StoryBibleContextResolver resolver;
    @Mock private AgentWorkingSetPromotionService workingSetPromotions;

    @Test
    void should_search_only_the_run_bound_epoch_and_promote_returned_nodes() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonCodec jsonCodec = new JacksonJsonCodec(objectMapper);
        ContextEpochSnapshotCodec codec = new ContextEpochSnapshotCodec(jsonCodec);
        StoryBibleSearchApplicationService service = new StoryBibleSearchApplicationService(
                runs, epochs, codec, resolver, workingSetPromotions, jsonCodec);
        var catalog = List.of(new StoryBibleRouteRequest.CatalogEntry(
                71L, "CHARACTER", "CHARACTER", "Mira", List.of("Captain"), "Pilot", List.of(), "",
                "AUTO_RETRIEVE", "CANON"));
        var snapshot = new ContextEpochSnapshotCodec.Snapshot(
                1, 101L, 11L, 9L, 4L, 301L, List.of(), catalog);
        Map<String, Object> state = Map.of("title", "Mira", "rank", "captain");
        when(epochs.loadVerifiedSnapshot(88001L)).thenReturn(codec.encode(snapshot));
        StoryBibleRelation relation = new StoryBibleRelation();
        relation.setRelationId(81L);
        relation.setSourceNodeId(71L);
        relation.setTargetNodeId(72L);
        relation.setRelationType("ALLY_OF");
        relation.setDescription("Trusted ally");
        relation.setAttributesJson("{}");
        when(resolver.resolve(any())).thenReturn(new StoryBibleContextResolver.ResolvedContext(
                new StoryBibleRouteDecision(StoryBibleRoutingMode.RETRIEVAL, List.of(71L),
                        Map.of(71L, "exact_alias"), false, 0L, false, List.of()),
                List.of(new StoryBibleContextResolver.RenderedNode(
                        71L, "Mira", "CHARACTER", state, List.of(91L), true)),
                List.of(relation)
        ));

        var result = service.execute(context(88001L),
                request("{\"query\":\"Mira\",\"mentionedEntities\":[\"Captain\"]}"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput()).contains("\"nodeId\":\"71\"")
                .contains("\"progressionIds\":[\"91\"]")
                .contains("\"aliases\":[\"Captain\"]")
                .contains("\"relationType\":\"ALLY_OF\"")
                .contains("\"otherNodeId\":\"72\"")
                .contains("story-bible:88001:71")
                .contains("captain");
        ArgumentCaptor<StoryBibleRouteRequest> route = ArgumentCaptor.forClass(StoryBibleRouteRequest.class);
        verify(resolver).resolve(route.capture());
        assertThat(route.getValue().epochCatalog()).isEqualTo(catalog);
        assertThat(route.getValue().chapterId()).isEqualTo(301L);
        assertThat(route.getValue().routingMode()).isEqualTo(StoryBibleRoutingMode.RETRIEVAL);
        verify(workingSetPromotions).promoteBestEffort(90001L, 50001L, List.of(71L), BigDecimal.ONE);
    }

    @Test
    void should_fail_without_a_run_bound_context_epoch() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonCodec jsonCodec = new JacksonJsonCodec(objectMapper);
        StoryBibleSearchApplicationService service = new StoryBibleSearchApplicationService(
                runs, epochs, new ContextEpochSnapshotCodec(jsonCodec), resolver, workingSetPromotions, jsonCodec);
        assertThat(service.execute(context(null), request("{\"query\":\"Mira\"}"))).extracting(
                value -> value.status(), value -> value.errorCode())
                .containsExactly("FAILED", "STORY_BIBLE_EPOCH_MISSING");
    }

    private ToolCallRequest request(String args) {
        return new ToolCallRequest(70001L, "story_bible_search", args, "idem", 1,
                "call-1", "[]", "[]", null, null, null, 1L);
    }

    private AuthorizedAgentRunContext context(Long contextEpochId) {
        return com.penmate.backend.application.agent.tool.runtime.AgentToolTestContext.context(
                101L, 70001L, 90001L, 50001L, 201L, contextEpochId, 1L, 301L, "trace-1");
    }
}
