package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateProjectCommand;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 书籍（当前落在 {@code NovelProject}）增删改查 tool 处理器。
 * <p>该 handler 采用“单一 {@code toolCode} + {@code operation} 二级分发”的复合工具模式：
 * 对外统一暴露 {@code book_crud}，内部再按 {@code create/list/update/delete} 分派到具体应用服务动作。</p>
 * <p>它只负责参数校验、operation 路由和结果格式化；审批判定、待审批快照落库与恢复续跑不在此类中处理。</p>
 */
@Component
@Slf4j
public class BookCrudToolHandler implements AgentToolHandler {

    private final NovelApplicationService novelApplicationService;
    private final JsonCodec jsonCodec;

    public BookCrudToolHandler(NovelApplicationService novelApplicationService, JsonCodec jsonCodec) {
        this.novelApplicationService = novelApplicationService;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public String toolCode() {
        return "book_crud";
    }

    @Override
    public boolean mutatesState(ToolCallRequest request) {
        String operation = JsonValues.string(jsonCodec.readObject(request.toolArgsJson()), "operation");
        return !"list".equalsIgnoreCase(operation);
    }

    @Override
    public void validate(ToolCallRequest request) {
        Map<String, Object> args;
        try {
            args = jsonCodec.readObject(request.toolArgsJson());
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid tool args", ex);
        }
        String operation = JsonValues.string(args, "operation");
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation is required");
        }
        if (!("create".equalsIgnoreCase(operation)
                || "list".equalsIgnoreCase(operation)
                || "update".equalsIgnoreCase(operation)
                || "delete".equalsIgnoreCase(operation))) {
            throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
        if ("create".equalsIgnoreCase(operation)) {
            rejectUnexpectedFields(args, operation, Set.of("operation", "ownerUserId", "title", "summary", "status"));
            if (JsonValues.longValue(args, "ownerUserId") == null) {
                throw new IllegalArgumentException("ownerUserId is required");
            }
            String title = JsonValues.nullableString(args, "title");
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title is required");
            }
        }
        if ("list".equalsIgnoreCase(operation)) {
            rejectUnexpectedFields(args, operation, Set.of("operation"));
        }
        if ("update".equalsIgnoreCase(operation)) {
            rejectUnexpectedFields(args, operation, Set.of("operation", "projectId", "title", "summary", "status"));
            if (JsonValues.longValue(args, "projectId") == null) {
                throw new IllegalArgumentException("projectId is required");
            }
        }
        if ("delete".equalsIgnoreCase(operation)) {
            rejectUnexpectedFields(args, operation, Set.of("operation", "projectId"));
            if (JsonValues.longValue(args, "projectId") == null) {
                throw new IllegalArgumentException("projectId is required");
            }
        }
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        try {
            Map<String, Object> args = jsonCodec.readObject(request.toolArgsJson());
            String operation = JsonValues.string(args, "operation");
            if ("create".equalsIgnoreCase(operation)) {
                NovelProject created = novelApplicationService.createProject(
                        new CreateProjectCommand(
                                JsonValues.longValue(args, "ownerUserId"),
                                JsonValues.nullableString(args, "title"),
                                JsonValues.nullableString(args, "summary"),
                                JsonValues.integerValue(args, "status")
                        ),
                        request.traceId()
                );
                log.info("book_crud 创建成功: newProjectId={}, traceId={}", created.getProjectId(), request.traceId());
                return ToolCallResult.success(toOutput(created));
            }
            if ("list".equalsIgnoreCase(operation)) {
                List<NovelProject> projects = novelApplicationService.listProjects();
                log.info("book_crud 查询成功: count={}, traceId={}", projects.size(), request.traceId());
                return ToolCallResult.success(toListOutput(projects));
            }
            if ("update".equalsIgnoreCase(operation)) {
                Long projectId = JsonValues.longValue(args, "projectId");
                NovelProject updated = novelApplicationService.updateProject(
                        projectId,
                        new UpdateProjectCommand(
                                JsonValues.nullableString(args, "title"),
                                JsonValues.nullableString(args, "summary"),
                                JsonValues.integerValue(args, "status")),
                        request.traceId()
                );
                log.info("book_crud 更新成功: projectId={}, traceId={}", updated.getProjectId(), request.traceId());
                return ToolCallResult.success(toOutput(updated));
            }
            if ("delete".equalsIgnoreCase(operation)) {
                Long projectId = JsonValues.longValue(args, "projectId");
                if (projectId == null) {
                    throw new IllegalArgumentException("projectId is required");
                }
                novelApplicationService.deleteProject(projectId, request.operatorId(), request.traceId());
                log.info("book_crud 删除成功: projectId={}, operatorId={}, traceId={}", projectId, request.operatorId(), request.traceId());
                return ToolCallResult.success(jsonCodec.write(Map.of(
                        "result", "deleted", "projectId", projectId)));
            }
            return new ToolCallResult("FAILED", null, null, "UNSUPPORTED_OPERATION", "Unsupported operation: " + operation);
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            return new ToolCallResult("FAILED", null, null, "BOOK_CRUD_EXECUTION_FAILED", ex.getMessage());
        }
    }

    private String toOutput(NovelProject project) {
        return jsonCodec.write(toOutputMap(project));
    }

    private String toListOutput(List<NovelProject> projects) {
        return jsonCodec.write(projects.stream()
                .map(this::toOutputMap)
                .toList());
    }

    private Map<String, Object> toOutputMap(NovelProject project) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("projectId", project.getProjectId());
        output.put("title", project.getTitle());
        output.put("summary", project.getSummary());
        output.put("status", project.getStatus());
        return output;
    }

    private void rejectUnexpectedFields(Map<String, Object> args, String operation, Set<String> allowedFields) {
        for (String fieldName : args.keySet()) {
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException("Unexpected field for operation " + operation + ": " + fieldName);
            }
        }
    }
}
