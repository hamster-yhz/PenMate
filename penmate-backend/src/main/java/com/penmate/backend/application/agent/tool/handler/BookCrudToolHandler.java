package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateProjectCommand;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
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

    public BookCrudToolHandler(NovelApplicationService novelApplicationService) {
        this.novelApplicationService = novelApplicationService;
    }

    @Override
    public String toolCode() {
        return "book_crud";
    }

    @Override
    public boolean mutatesState(ToolCallRequest request) {
        String operation = AgentJsonCodec.getString(AgentJsonCodec.parseObj(request.toolArgsJson()), "operation");
        return !"list".equalsIgnoreCase(operation);
    }

    @Override
    public void validate(ToolCallRequest request) {
        try {
            JSONObject args = AgentJsonCodec.parseObj(request.toolArgsJson());
            String operation = AgentJsonCodec.getString(args, "operation");
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
                if (args.getLong("ownerUserId") == null) {
                    throw new IllegalArgumentException("ownerUserId is required");
                }
                String title = args.getStr("title");
                if (title == null || title.isBlank()) {
                    throw new IllegalArgumentException("title is required");
                }
            }
            if ("list".equalsIgnoreCase(operation)) {
                rejectUnexpectedFields(args, operation, Set.of("operation"));
            }
            if ("update".equalsIgnoreCase(operation)) {
                rejectUnexpectedFields(args, operation, Set.of("operation", "projectId", "title", "summary", "status"));
                if (args.getLong("projectId") == null) {
                    throw new IllegalArgumentException("projectId is required");
                }
            }
            if ("delete".equalsIgnoreCase(operation)) {
                rejectUnexpectedFields(args, operation, Set.of("operation", "projectId"));
                if (args.getLong("projectId") == null) {
                    throw new IllegalArgumentException("projectId is required");
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid tool args", ex);
        }
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        try {
            JSONObject args = AgentJsonCodec.parseObj(request.toolArgsJson());
            String operation = AgentJsonCodec.getString(args, "operation");
            if ("create".equalsIgnoreCase(operation)) {
                NovelProject created = novelApplicationService.createProject(
                        new CreateProjectCommand(
                                args.getLong("ownerUserId"),
                                args.getStr("title"),
                                args.getStr("summary"),
                                args.getInt("status")
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
                Long projectId = args.getLong("projectId");
                NovelProject updated = novelApplicationService.updateProject(
                        projectId,
                        new UpdateProjectCommand(args.getStr("title"), args.getStr("summary"), args.getInt("status")),
                        request.traceId()
                );
                log.info("book_crud 更新成功: projectId={}, traceId={}", updated.getProjectId(), request.traceId());
                return ToolCallResult.success(toOutput(updated));
            }
            if ("delete".equalsIgnoreCase(operation)) {
                Long projectId = args.getLong("projectId");
                if (projectId == null) {
                    throw new IllegalArgumentException("projectId is required");
                }
                novelApplicationService.deleteProject(projectId, request.operatorId(), request.traceId());
                log.info("book_crud 删除成功: projectId={}, operatorId={}, traceId={}", projectId, request.operatorId(), request.traceId());
                return ToolCallResult.success("{\"result\":\"deleted\",\"projectId\":" + projectId + "}");
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
        return AgentJsonCodec.toJson(toOutputMap(project));
    }

    private String toListOutput(List<NovelProject> projects) {
        return AgentJsonCodec.toJson(projects.stream()
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

    private void rejectUnexpectedFields(JSONObject args, String operation, Set<String> allowedFields) {
        for (String fieldName : args.keySet()) {
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException("Unexpected field for operation " + operation + ": " + fieldName);
            }
        }
    }
}
