package com.penmate.backend.application.agent;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.json.AgentJsons;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateProjectCommand;
import com.penmate.backend.domain.novel.model.NovelProject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 书籍（小说项目）CRUD 工具处理器。
 * <p>
 * 该 handler 负责把 {@code book_crud} 的 JSON 参数解释为具体的小说项目创建、查询、更新、删除操作，
 * 并把领域对象转换回统一的 tool 输出 JSON。
 * </p>
 * <p>
 * 它只关心业务校验与执行，不负责审批判断、审批单创建或任务状态机控制；这些横切职责由上层
 * {@link ToolInvocationGateway} 与审批相关组件统一处理。
 * </p>
 */
@Component
@Slf4j
public class BookCrudAgentToolHandler implements AgentToolHandler {

    /** 小说项目应用服务，负责真正的书籍 CRUD 业务。 */
    private final NovelApplicationService novelApplicationService;

    public BookCrudAgentToolHandler(NovelApplicationService novelApplicationService) {
        this.novelApplicationService = novelApplicationService;
    }

    @Override
    public String toolCode() {
        return "book_crud";
    }

    /**
     * 校验 {@code book_crud} 请求参数。
     * <p>
     * 当前重点增强 delete 场景：删除请求只能包含最小必要字段，
     * 这样审批展示与最终执行看到的删除意图会更单一，避免混入无关字段造成语义歧义。
     * </p>
     *
     * @param request tool 调用请求
     */
    @Override
    public void validate(ToolInvocationRequest request) {
        try {
            JSONObject root = AgentJsons.parseObj(request.toolArgsJson());
            String operation = AgentJsons.getString(root, "operation");
            log.debug("校验 book_crud 请求: operation={}, taskId={}, traceId={}", operation, request.taskId(), request.traceId());
            if ("delete".equals(operation)) {
                requireOnlyFields(root, "operation", "projectId");
                requirePositiveLong(root, "projectId");
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid book_crud request", ex);
        }
    }

    /**
     * 执行 {@code book_crud} 工具。
     *
     * @param request tool 调用请求
     * @return 统一网关结果
     */
    @Override
    public ToolInvocationGatewayResult execute(ToolInvocationRequest request) {
        try {
            JSONObject root = AgentJsons.parseObj(request.toolArgsJson());
            String operation = AgentJsons.getString(root, "operation");
            log.info("执行 book_crud 工具: operation={}, projectId={}, taskId={}, operatorId={}, traceId={}",
                    operation, request.projectId(), request.taskId(), request.operatorId(), request.traceId());
            // 统一以 operation 做分派，让 agent 只需理解一个 toolCode，
            // 后端内部再映射到具体的小说项目应用服务调用。
            if ("create".equals(operation)) {
                NovelProject created = novelApplicationService.createProject(new CreateProjectCommand(
                        root.getLong("ownerUserId", 0L),
                        root.getStr("title", ""),
                        root.getStr("summary", null),
                        readNullableInt(root, "status")
                ), request.traceId());
                log.info("book_crud 创建成功: newProjectId={}, traceId={}", created.getProjectId(), request.traceId());
                return ToolInvocationGatewayResult.success(toOutput(created));
            }
            if ("list".equals(operation)) {
                List<NovelProject> projects = novelApplicationService.listProjects();
                log.info("book_crud 查询成功: count={}, traceId={}", projects.size(), request.traceId());
                return ToolInvocationGatewayResult.success(toListOutput(projects));
            }
            if ("update".equals(operation)) {
                NovelProject updated = novelApplicationService.updateProject(
                        requirePositiveLong(root, "projectId"),
                        new UpdateProjectCommand(
                                root.getStr("title", ""),
                                root.getStr("summary", null),
                                readNullableInt(root, "status")
                        ),
                        request.traceId()
                );
                log.info("book_crud 更新成功: projectId={}, traceId={}", updated.getProjectId(), request.traceId());
                return ToolInvocationGatewayResult.success(toOutput(updated));
            }
            if ("delete".equals(operation)) {
                long projectId = requirePositiveLong(root, "projectId");
                novelApplicationService.deleteProject(projectId, request.operatorId(), request.traceId());
                log.info("book_crud 删除成功: projectId={}, operatorId={}, traceId={}", projectId, request.operatorId(), request.traceId());
                return ToolInvocationGatewayResult.success("{\"result\":\"deleted\",\"projectId\":" + projectId + "}");
            }
            throw new IllegalArgumentException("Unsupported book_crud operation: " + operation);
        } catch (Exception ex) {
            log.error("执行 book_crud 工具失败: toolCode={}, taskId={}, traceId={}", request.toolCode(), request.taskId(), request.traceId(), ex);
            throw new IllegalArgumentException("Failed to execute book_crud tool", ex);
        }
    }

    /**
     * 将单个项目转换为 tool 输出 JSON。
     *
     * @param created 已创建或更新后的项目
     * @return 输出 JSON
     * @throws Exception JSON 序列化异常
     */
    private String toOutput(NovelProject created) throws Exception {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("projectId", created.getProjectId());
        output.put("title", created.getTitle());
        output.put("summary", created.getSummary());
        output.put("status", created.getStatus());
        return AgentJsons.toJson(output);
    }

    /**
     * 将项目列表转换为 tool 输出 JSON 数组。
     *
     * @param projects 项目列表
     * @return 输出 JSON
     * @throws Exception JSON 序列化异常
     */
    private String toListOutput(List<NovelProject> projects) throws Exception {
        List<Map<String, Object>> output = new ArrayList<>();
        for (NovelProject project : projects) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", project.getProjectId());
            item.put("title", project.getTitle());
            item.put("summary", project.getSummary());
            item.put("status", project.getStatus());
            output.add(item);
        }
        return AgentJsons.toJson(output);
    }

    /**
     * 读取并校验正整数 ID 字段。
     *
     * @param root 参数 JSON 根节点
     * @param fieldName 目标字段名
     * @return 合法的正整数值
     */
    private long requirePositiveLong(JSONObject root, String fieldName) {
        Long value = root.getLong(fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private Integer readNullableInt(JSONObject root, String fieldName) {
        return root.containsKey(fieldName) ? root.getInt(fieldName) : null;
    }

    /**
     * 限制 JSON 中只出现允许字段。
     * <p>
     * 主要用于 delete 场景，避免混入与删除无关的字段，
     * 使审批展示内容、快照内容与最终执行语义保持一致。
     * </p>
     *
     * @param root 参数 JSON 根节点
     * @param allowedFields 允许字段集合
     */
    private void requireOnlyFields(JSONObject root, String... allowedFields) {
        java.util.Set<String> allowed = new java.util.HashSet<>(java.util.Arrays.asList(allowedFields));
        for (String fieldName : root.keySet()) {
            if (!allowed.contains(fieldName)) {
                throw new IllegalArgumentException("Unexpected field: " + fieldName);
            }
        }
    }
}
