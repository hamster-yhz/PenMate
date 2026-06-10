package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.todo.TodoPlanItemView;
import com.penmate.backend.application.todo.TodoPlanView;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Todo planner tool 处理器。
 */
@Component
@Slf4j
public class TodoPlannerToolHandler implements AgentToolHandler {

    private static final Set<String> ALLOWED_PLANNING_MODES = Set.of(
            "TASK_BREAKDOWN",
            "QUALITY_REMEDIATION",
            "FOLLOW_UP_MODIFICATION"
    );

    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of(
            "USER_REQUEST",
            "QUALITY_REVIEW",
            "STORY_BIBLE_UPDATE",
            "PLANNING"
    );

    private static final Set<String> ALLOWED_PRIORITIES = Set.of(
            "P0",
            "P1",
            "P2",
            "P3"
    );

    private static final Set<String> ALLOWED_RECOMMENDED_STATUSES = Set.of(
            "TODO",
            "IN_PROGRESS",
            "BLOCKED",
            "DONE"
    );

    private final AgentModelRoutingService agentModelRoutingService;
    private final AgentLlmGateway agentLlmGateway;

    public TodoPlannerToolHandler(AgentModelRoutingService agentModelRoutingService,
                                  AgentLlmGateway agentLlmGateway) {
        this.agentModelRoutingService = agentModelRoutingService;
        this.agentLlmGateway = agentLlmGateway;
    }

    @Override
    public String toolCode() {
        return "todo_planner";
    }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        TodoPlannerCommand command = parseCommand(request.toolArgsJson());
        validateCommand(command);
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        if (request == null) {
            log.warn("todo_planner 参数非法: runId=null, traceId=null, message=request must not be null");
            return new ToolCallResult("FAILED", null, null, "TODO_PLANNER_FAILED", "request must not be null");
        }
        TodoPlannerCommand command;
        try {
            command = parseCommand(request.toolArgsJson());
            validateCommand(command);
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "todo planner execution failed"
                    : ex.getMessage();
            log.warn("todo_planner 参数非法: runId={}, traceId={}, message={}",
                    request == null ? null : request.runId(),
                    request == null ? null : request.traceId(),
                    message);
            return new ToolCallResult("FAILED", null, null, "TODO_PLANNER_FAILED", message);
        }

        try {
            AgentLlmExecutionConfig executionConfig = agentModelRoutingService.resolveExecutionConfig(
                    request.operatorId(),
                    null,
                    request.traceId()
            );
            AgentLlmTurnResponse response = agentLlmGateway.generateTurn(
                    new AgentLlmTurnRequest(List.of(AgentLlmMessage.user(buildPrompt(command))), List.of(), "none"),
                    executionConfig
            );
            String planningJson = response.assistantText();
            TodoPlanView planView = parsePlan(planningJson);
            log.info("todo_planner 执行成功: projectId={}, runId={}, traceId={}, itemCount={}",
                    request.projectId(), request.runId(), request.traceId(), planView.items().size());
            return ToolCallResult.success(AgentJsonCodec.toJson(toOutputMap(planView)));
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "todo planner execution failed"
                    : ex.getMessage();
            log.warn("todo_planner 执行失败: projectId={}, runId={}, traceId={}, message={}",
                    request.projectId(), request.runId(), request.traceId(), errorMessage);
            return new ToolCallResult("FAILED", null, null, "TODO_PLANNER_FAILED", errorMessage);
        }
    }

    private TodoPlannerCommand parseCommand(String toolArgsJson) {
        try {
            JSONObject args = AgentJsonCodec.parseObj(toolArgsJson);
            return new TodoPlannerCommand(
                    AgentJsonCodec.getString(args, "planningMode"),
                    AgentJsonCodec.getString(args, "userRequest"),
                    toQualityIssues(args.getJSONArray("qualityIssues")),
                    toStringList(args.getJSONArray("storyBibleUpdates")),
                    toStringList(args.getJSONArray("planningContext")),
                    toStringList(args.getJSONArray("existingTodos"))
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("toolArgsJson must be valid JSON", ex);
        }
    }

    private List<QualityIssueInput> toQualityIssues(JSONArray array) {
        if (array == null) {
            return List.of();
        }
        List<QualityIssueInput> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            result.add(new QualityIssueInput(
                    AgentJsonCodec.getString(item, "severity"),
                    AgentJsonCodec.getString(item, "summary"),
                    AgentJsonCodec.getString(item, "suggestion")
            ));
        }
        return result;
    }

    private List<String> toStringList(JSONArray array) {
        return array == null ? List.of() : array.toList(String.class);
    }

    private void validateCommand(TodoPlannerCommand command) {
        if (!ALLOWED_PLANNING_MODES.contains(command.planningMode())) {
            throw new IllegalArgumentException("planningMode must be one of [TASK_BREAKDOWN, QUALITY_REMEDIATION, FOLLOW_UP_MODIFICATION]");
        }
        if ("TASK_BREAKDOWN".equals(command.planningMode()) && command.userRequest().isBlank()) {
            throw new IllegalArgumentException("userRequest must not be blank for TASK_BREAKDOWN");
        }
        if ("QUALITY_REMEDIATION".equals(command.planningMode()) && command.qualityIssues().isEmpty()) {
            throw new IllegalArgumentException("qualityIssues must not be empty for QUALITY_REMEDIATION");
        }
        if (!"TASK_BREAKDOWN".equals(command.planningMode())
                && command.userRequest().isBlank()
                && command.qualityIssues().isEmpty()
                && command.storyBibleUpdates().isEmpty()
                && command.planningContext().isEmpty()
                && command.existingTodos().isEmpty()) {
            throw new IllegalArgumentException("at least one planning input is required");
        }
        for (QualityIssueInput issue : command.qualityIssues()) {
            if (issue.severity().isBlank() || issue.summary().isBlank() || issue.suggestion().isBlank()) {
                throw new IllegalArgumentException("qualityIssues must contain severity, summary and suggestion");
            }
        }
    }

    private TodoPlanView parsePlan(String planningJson) {
        JSONObject jsonObject;
        try {
            jsonObject = AgentJsonCodec.parseObj(planningJson);
        } catch (Exception ex) {
            throw new IllegalStateException("todo planner result must be valid JSON", ex);
        }

        String planTitle = AgentJsonCodec.getString(jsonObject, "planTitle").trim();
        String planSummary = AgentJsonCodec.getString(jsonObject, "planSummary").trim();
        String recommendedNextAction = AgentJsonCodec.getString(jsonObject, "recommendedNextAction").trim();
        if (planTitle.isBlank() || planSummary.isBlank() || recommendedNextAction.isBlank()) {
            throw new IllegalStateException("todo planner result must contain planTitle, planSummary and recommendedNextAction");
        }

        JSONArray itemsArray = jsonObject.getJSONArray("items");
        if (itemsArray == null || itemsArray.isEmpty()) {
            throw new IllegalStateException("todo planner result must contain structured items");
        }

        List<TodoPlanItemView> items = new ArrayList<>();
        for (int i = 0; i < itemsArray.size(); i++) {
            JSONObject itemObject = itemsArray.getJSONObject(i);
            String title = AgentJsonCodec.getString(itemObject, "title").trim();
            String description = AgentJsonCodec.getString(itemObject, "description").trim();
            String priority = AgentJsonCodec.getString(itemObject, "priority").trim();
            String sourceType = AgentJsonCodec.getString(itemObject, "sourceType").trim();
            String recommendedStatus = AgentJsonCodec.getString(itemObject, "recommendedStatus").trim();
            String rationale = AgentJsonCodec.getString(itemObject, "rationale").trim();
            boolean hasSuggestedAutoCreate = itemObject.containsKey("suggestedAutoCreate");
            boolean suggestedAutoCreate = Boolean.TRUE.equals(itemObject.getBool("suggestedAutoCreate", false));
            List<String> acceptanceCriteria = toStringList(itemObject.getJSONArray("acceptanceCriteria"));
            List<String> dependsOn = toStringList(itemObject.getJSONArray("dependsOn"));
            if (title.isBlank()
                    || description.isBlank()
                    || priority.isBlank()
                    || sourceType.isBlank()
                    || recommendedStatus.isBlank()
                    || rationale.isBlank()
                    || !hasSuggestedAutoCreate
                    || acceptanceCriteria.isEmpty()) {
                throw new IllegalStateException("todo planner items must contain title, description, priority, sourceType, recommendedStatus, suggestedAutoCreate, rationale and acceptanceCriteria");
            }
            if (!ALLOWED_PRIORITIES.contains(priority)) {
                throw new IllegalStateException("todo planner priority must be one of [P0, P1, P2, P3]");
            }
            if (!ALLOWED_SOURCE_TYPES.contains(sourceType)) {
                throw new IllegalStateException("todo planner sourceType must be one of [USER_REQUEST, QUALITY_REVIEW, STORY_BIBLE_UPDATE, PLANNING]");
            }
            if (!ALLOWED_RECOMMENDED_STATUSES.contains(recommendedStatus)) {
                throw new IllegalStateException("todo planner recommendedStatus must be one of [TODO, IN_PROGRESS, BLOCKED, DONE]");
            }
            items.add(new TodoPlanItemView(
                    title,
                    description,
                    priority,
                    sourceType,
                    recommendedStatus,
                    suggestedAutoCreate,
                    rationale,
                    acceptanceCriteria,
                    dependsOn
            ));
        }
        return new TodoPlanView(planTitle, planSummary, recommendedNextAction, items);
    }

    private Map<String, Object> toOutputMap(TodoPlanView planView) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("planTitle", planView.planTitle());
        output.put("planSummary", planView.planSummary());
        output.put("recommendedNextAction", planView.recommendedNextAction());
        List<Map<String, Object>> items = new ArrayList<>();
        for (TodoPlanItemView item : planView.items()) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("title", item.title());
            itemMap.put("description", item.description());
            itemMap.put("priority", item.priority());
            itemMap.put("sourceType", item.sourceType());
            itemMap.put("recommendedStatus", item.recommendedStatus());
            itemMap.put("suggestedAutoCreate", item.suggestedAutoCreate());
            itemMap.put("rationale", item.rationale());
            itemMap.put("acceptanceCriteria", item.acceptanceCriteria());
            itemMap.put("dependsOn", item.dependsOn());
            items.add(itemMap);
        }
        output.put("items", items);
        return output;
    }

    private String buildPrompt(TodoPlannerCommand command) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是 Todo Planner。\n")
                .append("只输出 Todo 规划建议，不要直接创建或持久化 todo。\n")
                .append("输出必须是可直接渲染前端卡片的 JSON 对象，不要输出 markdown，不要输出 bullet list。\n")
                .append("顶层字段必须包含：planTitle、planSummary、recommendedNextAction、items。\n")
                .append("items 每项必须包含：title、description、priority、sourceType、recommendedStatus、suggestedAutoCreate、rationale、acceptanceCriteria、dependsOn。\n")
                .append("priority 只允许：P0、P1、P2、P3。\n")
                .append("sourceType 只允许：USER_REQUEST、QUALITY_REVIEW、STORY_BIBLE_UPDATE、PLANNING。\n")
                .append("recommendedStatus 只允许：TODO、IN_PROGRESS、BLOCKED、DONE。\n")
                .append("suggestedAutoCreate 只能表示建议，不允许直接持久化创建。\n")
                .append("planningMode: ").append(command.planningMode()).append("\n")
                .append("用户请求：").append(command.userRequest()).append("\n");
        appendQualityIssues(builder, command.qualityIssues());
        appendSection(builder, "故事圣经待同步项", command.storyBibleUpdates());
        appendSection(builder, "规划上下文", command.planningContext());
        appendSection(builder, "现有待办", command.existingTodos());
        builder.append("请将用户任务拆解、质量问题转待办、后续修改规划统一整理。\n");
        return builder.toString().trim();
    }

    private void appendQualityIssues(StringBuilder builder, List<QualityIssueInput> issues) {
        builder.append("质量问题:\n");
        for (QualityIssueInput issue : issues) {
            builder.append("- [")
                    .append(issue.severity())
                    .append("] ")
                    .append(issue.summary())
                    .append("；建议：")
                    .append(issue.suggestion())
                    .append("\n");
        }
    }

    private void appendSection(StringBuilder builder, String title, List<String> items) {
        builder.append(title).append(":\n");
        for (String item : items) {
            builder.append("- ").append(item == null ? "" : item).append("\n");
        }
    }

    private record TodoPlannerCommand(
            String planningMode,
            String userRequest,
            List<QualityIssueInput> qualityIssues,
            List<String> storyBibleUpdates,
            List<String> planningContext,
            List<String> existingTodos
    ) {
        private TodoPlannerCommand {
            planningMode = planningMode == null ? "" : planningMode.trim();
            userRequest = userRequest == null ? "" : userRequest.trim();
            qualityIssues = qualityIssues == null ? List.of() : List.copyOf(qualityIssues);
            storyBibleUpdates = storyBibleUpdates == null ? List.of() : List.copyOf(storyBibleUpdates);
            planningContext = planningContext == null ? List.of() : List.copyOf(planningContext);
            existingTodos = existingTodos == null ? List.of() : List.copyOf(existingTodos);
        }
    }

    private record QualityIssueInput(
            String severity,
            String summary,
            String suggestion
    ) {
        private QualityIssueInput {
            severity = severity == null ? "" : severity.trim();
            summary = summary == null ? "" : summary.trim();
            suggestion = suggestion == null ? "" : suggestion.trim();
        }
    }
}
