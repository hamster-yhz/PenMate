package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.support.QualityReportView;
import com.penmate.backend.application.agent.tool.support.RevisionSuggestionView;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quality review tool 处理器。
 */
@Component
@Slf4j
public class QualityReviewToolHandler implements AgentToolHandler {

    private final AgentRepository agentRepository;
    private final AgentModelRoutingService agentModelRoutingService;
    private final AgentLlmGateway agentLlmGateway;

    public QualityReviewToolHandler(AgentRepository agentRepository,
                                    AgentModelRoutingService agentModelRoutingService,
                                    AgentLlmGateway agentLlmGateway) {
        this.agentRepository = agentRepository;
        this.agentModelRoutingService = agentModelRoutingService;
        this.agentLlmGateway = agentLlmGateway;
    }

    @Override
    public String toolCode() {
        return "quality_review";
    }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        QualityReviewCommand command = parseCommand(request.toolArgsJson());
        validateCommand(command);
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        QualityReviewCommand command;
        try {
            command = parseCommand(request.toolArgsJson());
            validateCommand(command);
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "quality review execution failed"
                    : ex.getMessage();
            log.warn("quality_review 参数非法: taskId={}, traceId={}, message={}",
                    request == null ? null : request.taskId(),
                    request == null ? null : request.traceId(),
                    message);
            return new ToolCallResult("FAILED", null, null, "QUALITY_REVIEW_FAILED", message);
        }

        try {
            AgentGenerationTask task = requireGenerationTask(request);
            AgentLlmExecutionConfig executionConfig = agentModelRoutingService.resolveExecutionConfig(
                    task.getUserId(),
                    task.getModelConfigId(),
                    request.traceId()
            );
            AgentGenerationTask reviewTask = buildToolGenerationTask(task, command, request.traceId());
            String reviewJson = agentLlmGateway.generate(reviewTask, List.of(), "", executionConfig);
            QualityReportView reportView = parseReport(reviewJson, command);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("score", reportView.score());
            output.put("passes", reportView.passes());
            output.put("issues", reportView.issues());
            output.put("needsRevision", reportView.needsRevision());
            output.put("riskFlags", reportView.riskFlags());
            output.put("revisionSuggestions", toSuggestionMaps(reportView.revisionSuggestions()));
            output.put("currentRevisionRound", reportView.currentRevisionRound());
            output.put("maxRevisionRounds", reportView.maxRevisionRounds());
            output.put("revisionAllowed", reportView.revisionAllowed());
            output.put("reviewSummary", reportView.reviewSummary());
            log.info("quality_review 执行成功: projectId={}, taskId={}, traceId={}, needsRevision={}, revisionAllowed={}",
                    request.projectId(), request.taskId(), request.traceId(),
                    reportView.needsRevision(), reportView.revisionAllowed());
            return ToolCallResult.success(AgentJsonCodec.toJson(output));
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "quality review execution failed"
                    : ex.getMessage();
            log.warn("quality_review 执行失败: projectId={}, taskId={}, traceId={}, message={}",
                    request.projectId(), request.taskId(), request.traceId(), errorMessage);
            return new ToolCallResult("FAILED", null, null, "QUALITY_REVIEW_FAILED", errorMessage);
        }
    }

    private QualityReviewCommand parseCommand(String toolArgsJson) {
        try {
            JSONObject args = AgentJsonCodec.parseObj(toolArgsJson);
            return new QualityReviewCommand(
                    AgentJsonCodec.getString(args, "draftText"),
                    toStringList(args.getJSONArray("userRequirements")),
                    toStringList(args.getJSONArray("personaProfile")),
                    toStringList(args.getJSONArray("storyOutline")),
                    toStringList(args.getJSONArray("timelineConstraints")),
                    toStringList(args.getJSONArray("worldRules")),
                    toStringList(args.getJSONArray("characterKnowledgeBoundaries")),
                    args.getInt("currentRevisionRound", 0),
                    args.getInt("maxRevisionRounds", 0)
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("toolArgsJson must be valid JSON", ex);
        }
    }

    private List<String> toStringList(JSONArray array) {
        return array == null ? List.of() : array.toList(String.class);
    }

    private void validateCommand(QualityReviewCommand command) {
        if (command.draftText().isBlank()) {
            throw new IllegalArgumentException("draftText must not be blank");
        }
        requireNonEmptyList(command.userRequirements(), "userRequirements");
        requireNonEmptyList(command.personaProfile(), "personaProfile");
        requireNonEmptyList(command.storyOutline(), "storyOutline");
        requireNonEmptyList(command.timelineConstraints(), "timelineConstraints");
        requireNonEmptyList(command.worldRules(), "worldRules");
        requireNonEmptyList(command.characterKnowledgeBoundaries(), "characterKnowledgeBoundaries");
        if (command.maxRevisionRounds() < 0) {
            throw new IllegalArgumentException("maxRevisionRounds must be greater than or equal to 0");
        }
        if (command.currentRevisionRound() < 0 || command.currentRevisionRound() > command.maxRevisionRounds()) {
            throw new IllegalArgumentException("currentRevisionRound must be between 0 and maxRevisionRounds");
        }
    }

    private void requireNonEmptyList(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
    }

    private QualityReportView parseReport(String reviewJson, QualityReviewCommand command) {
        JSONObject jsonObject;
        try {
            jsonObject = AgentJsonCodec.parseObj(reviewJson);
        } catch (Exception ex) {
            throw new IllegalStateException("quality review result must be valid JSON", ex);
        }
        JSONArray issuesArray = jsonObject.getJSONArray("issues");
        if (issuesArray == null || issuesArray.isEmpty()) {
            throw new IllegalStateException("quality review result must contain structured issues");
        }
        JSONArray revisionSuggestionsArray = jsonObject.getJSONArray("revisionSuggestions");
        if (revisionSuggestionsArray == null) {
            throw new IllegalStateException("quality review result must contain revisionSuggestions");
        }
        String reviewSummary = AgentJsonCodec.getString(jsonObject, "reviewSummary").trim();
        if (reviewSummary.isBlank() || "质量良好".equals(reviewSummary)) {
            throw new IllegalStateException("quality review summary must be meaningful");
        }

        List<Map<String, String>> issues = new ArrayList<>();
        for (int i = 0; i < issuesArray.size(); i++) {
            JSONObject issueObject = issuesArray.getJSONObject(i);
            String dimension = AgentJsonCodec.getString(issueObject, "dimension").trim();
            String severity = AgentJsonCodec.getString(issueObject, "severity").trim();
            String summary = AgentJsonCodec.getString(issueObject, "summary").trim();
            String evidence = AgentJsonCodec.getString(issueObject, "evidence").trim();
            String suggestion = AgentJsonCodec.getString(issueObject, "suggestion").trim();
            if (dimension.isBlank() || severity.isBlank() || summary.isBlank() || evidence.isBlank() || suggestion.isBlank()) {
                throw new IllegalStateException("quality review issues must contain dimension, severity, summary, evidence and suggestion");
            }
            Map<String, String> issue = new LinkedHashMap<>();
            issue.put("dimension", dimension);
            issue.put("severity", severity);
            issue.put("summary", summary);
            issue.put("evidence", evidence);
            issue.put("suggestion", suggestion);
            issues.add(issue);
        }

        List<RevisionSuggestionView> revisionSuggestions = new ArrayList<>();
        for (int i = 0; i < revisionSuggestionsArray.size(); i++) {
            JSONObject suggestionObject = revisionSuggestionsArray.getJSONObject(i);
            RevisionSuggestionView suggestionView = new RevisionSuggestionView(
                    AgentJsonCodec.getString(suggestionObject, "priority"),
                    AgentJsonCodec.getString(suggestionObject, "target"),
                    AgentJsonCodec.getString(suggestionObject, "instruction"),
                    AgentJsonCodec.getString(suggestionObject, "rationale")
            );
            if (suggestionView.priority().isBlank()
                    || suggestionView.target().isBlank()
                    || suggestionView.instruction().isBlank()
                    || suggestionView.rationale().isBlank()) {
                throw new IllegalStateException("revisionSuggestions must contain priority, target, instruction and rationale");
            }
            revisionSuggestions.add(suggestionView);
        }

        boolean needsRevision = Boolean.TRUE.equals(jsonObject.getBool("needsRevision", false));
        boolean revisionAllowed = command.currentRevisionRound() < command.maxRevisionRounds();
        return new QualityReportView(
                jsonObject.getInt("score", 0),
                toStringList(jsonObject.getJSONArray("passes")),
                issues,
                needsRevision,
                toStringList(jsonObject.getJSONArray("riskFlags")),
                revisionSuggestions,
                command.currentRevisionRound(),
                command.maxRevisionRounds(),
                revisionAllowed,
                reviewSummary
        );
    }

    private List<Map<String, String>> toSuggestionMaps(List<RevisionSuggestionView> suggestions) {
        List<Map<String, String>> result = new ArrayList<>();
        for (RevisionSuggestionView suggestion : suggestions) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("priority", suggestion.priority());
            item.put("target", suggestion.target());
            item.put("instruction", suggestion.instruction());
            item.put("rationale", suggestion.rationale());
            result.add(item);
        }
        return result;
    }

    private AgentGenerationTask requireGenerationTask(ToolCallRequest request) {
        AgentGenerationTask task = agentRepository.findGenerationTask(request.projectId(), request.taskId());
        if (task == null) {
            throw new IllegalStateException("generation task not found");
        }
        if (task.getUserId() == null) {
            throw new IllegalStateException("generation task userId is required");
        }
        if (task.getModelConfigId() == null) {
            throw new IllegalStateException("generation task modelConfigId is required");
        }
        return task;
    }

    private AgentGenerationTask buildToolGenerationTask(AgentGenerationTask source,
                                                        QualityReviewCommand command,
                                                        String traceId) {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(source.getId());
        task.setTaskId(source.getTaskId());
        task.setProjectId(source.getProjectId());
        task.setUserId(source.getUserId());
        task.setConversationId(source.getConversationId());
        task.setChapterId(source.getChapterId());
        task.setModelConfigId(source.getModelConfigId());
        task.setTaskType(source.getTaskType());
        task.setPluginSnapshot(source.getPluginSnapshot());
        task.setTraceId(traceId == null || traceId.isBlank() ? source.getTraceId() : traceId);
        task.setPromptSnapshot(buildPrompt(command));
        return task;
    }

    private String buildPrompt(QualityReviewCommand command) {
        StringBuilder builder = new StringBuilder();
        builder.append("请作为小说质量审查器，仅返回 JSON 对象，不要输出 markdown。\n")
                .append("输出字段必须包含：score、passes、issues、needsRevision、riskFlags、revisionSuggestions、reviewSummary。\n")
                .append("issues 每项必须包含：dimension、severity、summary、evidence、suggestion。\n")
                .append("revisionSuggestions 每项必须包含：priority、target、instruction、rationale。\n")
                .append("重点审查维度：用户要求、人设一致性、剧情逻辑、时间线、世界观、角色知识边界。\n")
                .append("待审正文：").append(command.draftText()).append("\n");
        appendSection(builder, "用户要求", command.userRequirements());
        appendSection(builder, "人设约束", command.personaProfile());
        appendSection(builder, "剧情提纲", command.storyOutline());
        appendSection(builder, "时间线约束", command.timelineConstraints());
        appendSection(builder, "世界观规则", command.worldRules());
        appendSection(builder, "角色知识边界", command.characterKnowledgeBoundaries());
        builder.append("当前修订轮次：").append(command.currentRevisionRound()).append("\n");
        builder.append("最大修订轮次：").append(command.maxRevisionRounds()).append("\n");
        builder.append("如果发现问题，不要只写“质量良好”，必须提供结构化 issues 列表与 revisionSuggestions。\n");
        return builder.toString().trim();
    }

    private void appendSection(StringBuilder builder, String title, List<String> items) {
        builder.append(title).append(":\n");
        for (String item : items) {
            builder.append("- ").append(item == null ? "" : item).append("\n");
        }
    }

    private record QualityReviewCommand(
            String draftText,
            List<String> userRequirements,
            List<String> personaProfile,
            List<String> storyOutline,
            List<String> timelineConstraints,
            List<String> worldRules,
            List<String> characterKnowledgeBoundaries,
            int currentRevisionRound,
            int maxRevisionRounds
    ) {
        private QualityReviewCommand {
            draftText = draftText == null ? "" : draftText.trim();
            userRequirements = userRequirements == null ? List.of() : List.copyOf(userRequirements);
            personaProfile = personaProfile == null ? List.of() : List.copyOf(personaProfile);
            storyOutline = storyOutline == null ? List.of() : List.copyOf(storyOutline);
            timelineConstraints = timelineConstraints == null ? List.of() : List.copyOf(timelineConstraints);
            worldRules = worldRules == null ? List.of() : List.copyOf(worldRules);
            characterKnowledgeBoundaries = characterKnowledgeBoundaries == null ? List.of() : List.copyOf(characterKnowledgeBoundaries);
        }
    }
}
