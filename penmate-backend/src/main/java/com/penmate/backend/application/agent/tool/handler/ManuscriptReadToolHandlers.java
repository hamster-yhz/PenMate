package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.ManuscriptReadApplicationService;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

final class ManuscriptReadToolHandlers { private ManuscriptReadToolHandlers() {} }

@Component
@RequiredArgsConstructor
class ManuscriptManifestToolHandler implements AgentToolHandler {
    private final ManuscriptReadApplicationService manuscripts;
    private final JsonCodec jsonCodec;
    @Override public String toolCode() { return "manuscript_manifest"; }
    @Override public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        try {
            Args args = jsonCodec.read(request.toolArgsJson(), Args.class);
            return ToolCallResult.success(jsonCodec.write(manuscripts.manifest(context.projectId(),
                    args == null || args.cursor() == null ? 0 : args.cursor(),
                    args == null || args.limit() == null ? 100 : args.limit())));
        } catch (RuntimeException exception) {
            return ToolCallResult.failed("MANUSCRIPT_MANIFEST_FAILED", message(exception));
        }
    }
    private String message(RuntimeException ex) { return ex.getMessage() == null ? "Manifest failed" : ex.getMessage(); }
    private record Args(Integer cursor, Integer limit) {}
}

@Component
@RequiredArgsConstructor
class ManuscriptChapterReadToolHandler implements AgentToolHandler {
    private final ManuscriptReadApplicationService manuscripts;
    private final JsonCodec jsonCodec;
    @Override public String toolCode() { return "manuscript_chapter_read"; }
    @Override public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        try {
            Args args = jsonCodec.read(request.toolArgsJson(), Args.class);
            return ToolCallResult.success(jsonCodec.write(manuscripts.read(context.projectId(),
                    args == null ? List.of() : args.selections())));
        } catch (RuntimeException exception) {
            return ToolCallResult.failed("MANUSCRIPT_CHAPTER_READ_FAILED", message(exception));
        }
    }
    private String message(RuntimeException ex) { return ex.getMessage() == null ? "Chapter read failed" : ex.getMessage(); }
    private record Args(List<ManuscriptReadApplicationService.Selection> selections) {}
}
