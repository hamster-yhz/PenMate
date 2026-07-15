package com.penmate.backend.application.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.context.AgentContextEpochService;
import com.penmate.backend.application.agent.context.AgentWorkingSetService;
import com.penmate.backend.application.agent.context.ContextEpochSnapshotCodec;
import com.penmate.backend.application.agent.context.StoryBibleContextResolver;
import com.penmate.backend.application.agent.context.StoryBibleRouteRequest;
import com.penmate.backend.application.agent.context.StoryBibleRoutingMode;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class StoryBibleSearchApplicationService {
    private final AgentRunRepository runs;
    private final AgentContextEpochService epochs;
    private final ContextEpochSnapshotCodec codec;
    private final StoryBibleContextResolver resolver;
    private final AgentWorkingSetService workingSet;
    private final ObjectMapper objectMapper;

    public StoryBibleSearchApplicationService(AgentRunRepository runs, AgentContextEpochService epochs,
                                               ContextEpochSnapshotCodec codec, StoryBibleContextResolver resolver,
                                               AgentWorkingSetService workingSet, ObjectMapper objectMapper) {
        this.runs = runs;
        this.epochs = epochs;
        this.codec = codec;
        this.resolver = resolver;
        this.workingSet = workingSet;
        this.objectMapper = objectMapper;
    }

    public ToolCallResult execute(ToolCallRequest request) {
        var run = runs.findRun(request.runId());
        var input = runs.findInput(request.runId());
        if (run == null || input == null || run.contextEpochId() == null) {
            return ToolCallResult.failed("STORY_BIBLE_EPOCH_MISSING", "Run has no bound Context Epoch");
        }
        SearchArgs args;
        try { args = objectMapper.readValue(request.toolArgsJson(), SearchArgs.class); }
        catch (JsonProcessingException ex) { return ToolCallResult.failed("STORY_BIBLE_SEARCH_INVALID", "Invalid search arguments"); }
        var snapshot = codec.decode(epochs.loadVerifiedSnapshot(run.contextEpochId()));
        var resolved = resolver.resolve(new StoryBibleRouteRequest(
                run.projectId(), run.sessionId(), run.runId(), input.chapterId(), args.query(), args.mentionedEntities(),
                StoryBibleRoutingMode.RETRIEVAL, snapshot.storyBibleRevision(), snapshot.selectorCatalog(), List.of(), null));
        List<Long> usedIds = resolved.nodes().stream().map(StoryBibleContextResolver.RenderedNode::nodeId).toList();
        workingSet.promote(run.sessionId(), run.turnId(), usedIds, BigDecimal.ONE);
        List<Map<String, Object>> results = resolved.nodes().stream().map(node -> Map.<String, Object>of(
                "nodeId", String.valueOf(node.nodeId()),
                "title", node.title(),
                "typeCode", node.typeCode(),
                "effectiveState", node.effectiveState(),
                "progressionIds", node.appliedProgressionIds().stream().map(String::valueOf).toList(),
                "citation", "story-bible:" + run.contextEpochId() + ":" + node.nodeId()
        )).toList();
        try { return ToolCallResult.success(objectMapper.writeValueAsString(results)); }
        catch (JsonProcessingException ex) { return ToolCallResult.failed("STORY_BIBLE_SEARCH_SERIALIZE", ex.getMessage()); }
    }

    public record SearchArgs(String query, List<String> mentionedEntities) {
        public SearchArgs {
            query = query == null ? "" : query.trim();
            mentionedEntities = List.copyOf(mentionedEntities == null ? List.of() : mentionedEntities);
        }
    }
}
