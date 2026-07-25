package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateProjectCommand;
import com.penmate.backend.domain.novel.model.NovelProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BookCrudToolHandler implements AgentToolHandler {

    private static final Set<String> OPERATIONS = Set.of("get", "update", "delete");
    private final NovelApplicationService novels;
    private final JsonCodec jsonCodec;

    @Override
    public String toolCode() {
        return "book_crud";
    }

    @Override
    public boolean mutatesState(AuthorizedAgentRunContext context, ToolCallRequest request) {
        return !"get".equalsIgnoreCase(operation(request));
    }

    @Override
    public void validate(AuthorizedAgentRunContext context, ToolCallRequest request) {
        Map<String, Object> args = args(request);
        String operation = operation(request);
        if (!OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("operation must be one of " + OPERATIONS);
        }
        Set<String> allowed = "update".equals(operation)
                ? Set.of("operation", "title", "summary", "status")
                : Set.of("operation");
        args.keySet().stream().filter(field -> !allowed.contains(field)).findFirst().ifPresent(field -> {
            throw new IllegalArgumentException("Unexpected field for operation " + operation + ": " + field);
        });
    }

    @Override
    public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        Map<String, Object> args = args(request);
        return switch (operation(request)) {
            case "get" -> ToolCallResult.success(jsonCodec.write(output(novels.getProject(context.projectId()))));
            case "update" -> ToolCallResult.success(jsonCodec.write(output(novels.updateProject(
                    context.projectId(),
                    new UpdateProjectCommand(
                            JsonValues.nullableString(args, "title"),
                            JsonValues.nullableString(args, "summary"),
                            JsonValues.integerValue(args, "status")),
                    context.traceId()))));
            case "delete" -> {
                novels.deleteProject(context.projectId(), context.ownerUserId(), context.traceId());
                yield ToolCallResult.success(jsonCodec.write(Map.of(
                        "result", "deleted", "projectId", String.valueOf(context.projectId()))));
            }
            default -> ToolCallResult.failed("UNSUPPORTED_OPERATION", "Unsupported operation");
        };
    }

    private Map<String, Object> args(ToolCallRequest request) {
        return jsonCodec.readObject(request.toolArgsJson());
    }

    private String operation(ToolCallRequest request) {
        String value = JsonValues.string(args(request), "operation");
        return value == null ? "" : value.trim().toLowerCase();
    }

    private Map<String, Object> output(NovelProject project) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("projectId", String.valueOf(project.getProjectId()));
        output.put("title", project.getTitle());
        output.put("summary", project.getSummary());
        output.put("status", project.getStatus());
        return output;
    }
}
