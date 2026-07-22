package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.context.AgentContextEpochService;
import com.penmate.backend.application.agent.context.AgentWorkingSetPromotionService;
import com.penmate.backend.application.agent.context.ContextEpochSnapshotCodec;
import com.penmate.backend.application.agent.context.StoryBibleContextResolver;
import com.penmate.backend.application.agent.context.StoryBibleRouteRequest;
import com.penmate.backend.application.agent.context.StoryBibleRoutingMode;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class StoryBibleSearchApplicationService {
    private final AgentRunRepository runs;
    private final AgentContextEpochService epochs;
    private final ContextEpochSnapshotCodec codec;
    private final StoryBibleContextResolver resolver;
    private final AgentWorkingSetPromotionService workingSetPromotions;
    private final JsonCodec jsonCodec;
    private final AgentModelRoutingService modelRouting;

    @Autowired
    public StoryBibleSearchApplicationService(AgentRunRepository runs, AgentContextEpochService epochs,
                                               ContextEpochSnapshotCodec codec, StoryBibleContextResolver resolver,
                                               AgentWorkingSetPromotionService workingSetPromotions,
                                               JsonCodec jsonCodec, AgentModelRoutingService modelRouting) {
        this.runs = runs;
        this.epochs = epochs;
        this.codec = codec;
        this.resolver = resolver;
        this.workingSetPromotions = workingSetPromotions;
        this.jsonCodec = jsonCodec;
        this.modelRouting = modelRouting;
    }

    StoryBibleSearchApplicationService(AgentRunRepository runs, AgentContextEpochService epochs,
                                       ContextEpochSnapshotCodec codec, StoryBibleContextResolver resolver,
                                       AgentWorkingSetPromotionService workingSetPromotions,
                                       JsonCodec jsonCodec) {
        this(runs, epochs, codec, resolver, workingSetPromotions, jsonCodec, null);
    }

    public ToolCallResult execute(ToolCallRequest request) {
        var run = runs.findRun(request.runId());
        var input = runs.findInput(request.runId());
        if (run == null || input == null || run.contextEpochId() == null) {
            return ToolCallResult.failed("STORY_BIBLE_EPOCH_MISSING", "Run has no bound Context Epoch");
        }
        SearchArgs args;
        try { args = jsonCodec.read(request.toolArgsJson(), SearchArgs.class); }
        catch (RuntimeException ex) { return ToolCallResult.failed("STORY_BIBLE_SEARCH_INVALID", "Invalid search arguments"); }
        var snapshot = codec.decode(epochs.loadVerifiedSnapshot(run.contextEpochId()));
        var epoch = modelRouting == null ? null : epochs.get(run.contextEpochId());
        StoryBibleRoutingMode routingMode = epoch == null ? StoryBibleRoutingMode.RETRIEVAL
                : StoryBibleRoutingMode.valueOf(epoch.routingMode());
        var selectorConfig = routingMode == StoryBibleRoutingMode.RETRIEVAL || modelRouting == null ? null
                : modelRouting.resolveExecutionConfig(run.ownerUserId(), epoch.routerModelConfigId(), request.traceId());
        var resolved = resolver.resolve(new StoryBibleRouteRequest(
                run.projectId(), run.sessionId(), run.runId(), input.chapterId(), args.query(), args.mentionedEntities(),
                routingMode, snapshot.storyBibleRevision(), snapshot.selectorCatalog(), List.of(), selectorConfig));
        List<Long> usedIds = resolved.nodes().stream().map(StoryBibleContextResolver.RenderedNode::nodeId).toList();
        workingSetPromotions.promoteBestEffort(run.sessionId(), run.turnId(), usedIds, BigDecimal.ONE);
        List<Map<String, Object>> results = resolved.nodes().stream().map(node -> Map.<String, Object>of(
                "nodeId", String.valueOf(node.nodeId()),
                "title", node.title(),
                "typeCode", node.typeCode(),
                "effectiveState", node.effectiveState(),
                "progressionIds", node.appliedProgressionIds().stream().map(String::valueOf).toList(),
                "citation", "story-bible:" + run.contextEpochId() + ":" + node.nodeId()
        )).toList();
        try { return ToolCallResult.success(jsonCodec.write(results)); }
        catch (RuntimeException ex) { return ToolCallResult.failed("STORY_BIBLE_SEARCH_SERIALIZE", ex.getMessage()); }
    }

    public record SearchArgs(String query, List<String> mentionedEntities) {
        public SearchArgs {
            query = query == null ? "" : query.trim();
            mentionedEntities = List.copyOf(mentionedEntities == null ? List.of() : mentionedEntities);
        }
    }
}
