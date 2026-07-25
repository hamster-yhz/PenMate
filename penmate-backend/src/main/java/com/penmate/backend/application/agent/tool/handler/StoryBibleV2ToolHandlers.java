package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.StoryBibleInspectApplicationService;
import com.penmate.backend.application.agent.tool.StoryBibleMutationToolExecutor;
import com.penmate.backend.application.agent.tool.StoryBibleMutationToolExecutor.OperationSpec;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class StoryBibleInspectToolHandler implements AgentToolHandler {
    private final StoryBibleInspectApplicationService service;

    StoryBibleInspectToolHandler(StoryBibleInspectApplicationService service) {
        this.service = service;
    }

    @Override public String toolCode() { return "story_bible_inspect"; }

    @Override public void validate(AuthorizedAgentRunContext context, ToolCallRequest request) {
        if (context == null || request == null) throw new IllegalArgumentException("Run context is required");
    }

    @Override public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        return service.execute(context, request);
    }
}

abstract class AbstractStoryBibleWriteToolHandler implements AgentToolHandler {
    private final StoryBibleMutationToolExecutor executor;
    private final Map<String, OperationSpec> operations;

    AbstractStoryBibleWriteToolHandler(StoryBibleMutationToolExecutor executor,
                                       Map<String, OperationSpec> operations) {
        this.executor = executor;
        this.operations = Map.copyOf(operations);
    }

    @Override public boolean mutatesState(AuthorizedAgentRunContext context, ToolCallRequest request) { return true; }

    @Override public void validate(AuthorizedAgentRunContext context, ToolCallRequest request) {
        if (context == null || request == null) throw new IllegalArgumentException("Run context is required");
        executor.validate(request, operations);
    }

    @Override public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        return executor.execute(context, request, operations);
    }
}

@Component
class StoryBibleNodeWriteToolHandler extends AbstractStoryBibleWriteToolHandler {
    StoryBibleNodeWriteToolHandler(StoryBibleMutationToolExecutor executor) {
        super(executor, Map.of(
                "create", OperationSpec.of("create_node", "typeId", "title"),
                "update", OperationSpec.of("update_node", "nodeId", "expectedRevision"),
                "archive", OperationSpec.of("delete_node", "nodeId", "expectedRevision")
        ));
    }

    @Override public String toolCode() { return "story_bible_node_write"; }
}

@Component
class StoryBibleRelationWriteToolHandler extends AbstractStoryBibleWriteToolHandler {
    StoryBibleRelationWriteToolHandler(StoryBibleMutationToolExecutor executor) {
        super(executor, Map.of(
                "create", OperationSpec.of("create_relation", "sourceNodeId", "relationType", "targetNodeId"),
                "update", OperationSpec.of("update_relation", "relationId", "expectedRevision"),
                "delete", OperationSpec.of("delete_relation", "relationId", "expectedRevision")
        ));
    }

    @Override public String toolCode() { return "story_bible_relation_write"; }
}

@Component
class StoryBibleProgressionWriteToolHandler extends AbstractStoryBibleWriteToolHandler {
    StoryBibleProgressionWriteToolHandler(StoryBibleMutationToolExecutor executor) {
        super(executor, Map.of(
                "create", OperationSpec.of("create_progression", "nodeId", "anchorChapterId", "patch"),
                "update", OperationSpec.of("update_progression", "progressionId", "expectedRevision"),
                "delete", OperationSpec.of("delete_progression", "progressionId", "expectedRevision")
        ));
    }

    @Override public String toolCode() { return "story_bible_progression_write"; }
}

@Component
class StoryBibleStructureWriteToolHandler extends AbstractStoryBibleWriteToolHandler {
    StoryBibleStructureWriteToolHandler(StoryBibleMutationToolExecutor executor) {
        super(executor, Map.ofEntries(
                Map.entry("create_type", OperationSpec.of("create_node_type", "typeCode", "semanticFamily", "displayName", "fieldSchema")),
                Map.entry("update_type", OperationSpec.of("update_node_type", "typeId")),
                Map.entry("archive_type", OperationSpec.of("archive_node_type", "typeId")),
                Map.entry("create_category", OperationSpec.of("create_category", "name")),
                Map.entry("update_category", OperationSpec.of("update_category", "categoryId")),
                Map.entry("delete_category", OperationSpec.of("delete_category", "categoryId")),
                Map.entry("create_tag", OperationSpec.of("create_tag", "name")),
                Map.entry("update_tag", OperationSpec.of("update_tag", "tagId")),
                Map.entry("delete_tag", OperationSpec.of("delete_tag", "tagId"))
        ));
    }

    @Override public String toolCode() { return "story_bible_structure_write"; }
}
