package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationService;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.support.QualityReportView;
import com.penmate.backend.application.agent.tool.support.QualityReviewCommand;
import com.penmate.backend.application.agent.tool.support.QualityReviewCommandParser;
import com.penmate.backend.application.agent.tool.support.RevisionSuggestionView;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DefaultQualityReviewApplicationService implements QualityReviewApplicationService {

    private final AgentModelRoutingService agentModelRoutingService;
    private final AgentLlmInvocationService llmInvocations;
    private final QualityReviewCommandParser qualityReviewCommandParser;
    private final NovelApplicationService novelApplicationService;
    private final JsonCodec jsonCodec;

    public DefaultQualityReviewApplicationService(AgentModelRoutingService agentModelRoutingService,
                                                  AgentLlmInvocationService llmInvocations,
                                                  QualityReviewCommandParser qualityReviewCommandParser,
                                                  JsonCodec jsonCodec) {
        this(agentModelRoutingService, llmInvocations, qualityReviewCommandParser, null, jsonCodec);
    }

    public DefaultQualityReviewApplicationService(AgentModelRoutingService agentModelRoutingService,
                                                  AgentLlmGateway llmGateway,
                                                  QualityReviewCommandParser qualityReviewCommandParser,
                                                  JsonCodec jsonCodec) {
        this(agentModelRoutingService, new AgentLlmInvocationService(llmGateway), qualityReviewCommandParser, null, jsonCodec);
    }

    public DefaultQualityReviewApplicationService(AgentModelRoutingService agentModelRoutingService,
                                                  AgentLlmGateway llmGateway,
                                                  QualityReviewCommandParser qualityReviewCommandParser,
                                                  NovelApplicationService novelApplicationService,
                                                  JsonCodec jsonCodec) {
        this(agentModelRoutingService, new AgentLlmInvocationService(llmGateway),
                qualityReviewCommandParser, novelApplicationService, jsonCodec);
    }

    @Autowired
    public DefaultQualityReviewApplicationService(AgentModelRoutingService agentModelRoutingService,
                                                  AgentLlmInvocationService llmInvocations,
                                                  QualityReviewCommandParser qualityReviewCommandParser,
                                                  NovelApplicationService novelApplicationService,
                                                  JsonCodec jsonCodec) {
        this.agentModelRoutingService = agentModelRoutingService;
        this.llmInvocations = llmInvocations;
        this.qualityReviewCommandParser = qualityReviewCommandParser;
        this.novelApplicationService = novelApplicationService;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public ToolCallResult review(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        QualityReviewCommand parsedCommand = qualityReviewCommandParser.parse(request.toolArgsJson());
        QualityReviewCommand command = isSparseIdentifierOnlyRequest(parsedCommand)
                ? enrichCommand(parsedCommand, request)
                : parsedCommand;
        qualityReviewCommandParser.validate(command);

        AgentLlmExecutionConfig executionConfig = agentModelRoutingService.resolveExecutionConfig(
                request.operatorId(),
                null,
                request.traceId()
        );
        AgentLlmTurnResponse response = llmInvocations.invokeBuffered(
                new AgentLlmTurnRequest(List.of(AgentLlmMessage.user(buildPrompt(command))), List.of(), "none"),
                executionConfig
        );
        QualityReportView reportView = parseReport(response.assistantText(), command);
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
        log.info("quality_review 执行成功: projectId={}, runId={}, traceId={}, needsRevision={}, revisionAllowed={}",
                request.projectId(), request.runId(), request.traceId(),
                reportView.needsRevision(), reportView.revisionAllowed());
        return ToolCallResult.success(jsonCodec.write(output));
    }

    private List<String> toStringList(List<?> values) {
        return values.stream()
                .filter(value -> value != null)
                .map(String::valueOf)
                .toList();
    }

    private QualityReportView parseReport(String reviewJson, QualityReviewCommand command) {
        Map<String, Object> jsonObject;
        try {
            jsonObject = jsonCodec.readObject(reviewJson);
        } catch (Exception ex) {
            throw new IllegalStateException("quality review result must be valid JSON", ex);
        }
        Object rawIssues = jsonObject.get("issues");
        if (!(rawIssues instanceof List<?> issuesArray)) {
            throw new IllegalStateException("quality review result must contain structured issues");
        }
        Object rawRevisionSuggestions = jsonObject.get("revisionSuggestions");
        if (!(rawRevisionSuggestions instanceof List<?> revisionSuggestionsArray)) {
            throw new IllegalStateException("quality review result must contain revisionSuggestions");
        }
        String reviewSummary = JsonValues.string(jsonObject, "reviewSummary").trim();
        if (reviewSummary.isBlank()) {
            throw new IllegalStateException("quality review summary must be meaningful");
        }

        List<Map<String, String>> issues = new ArrayList<>();
        for (Object issueItem : issuesArray) {
            Map<String, Object> issueObject = mapValue(issueItem);
            String dimension = JsonValues.string(issueObject, "dimension").trim();
            String severity = JsonValues.string(issueObject, "severity").trim();
            String summary = JsonValues.string(issueObject, "summary").trim();
            String evidence = JsonValues.string(issueObject, "evidence").trim();
            String suggestion = JsonValues.string(issueObject, "suggestion").trim();
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
        for (Object suggestionItem : revisionSuggestionsArray) {
            Map<String, Object> suggestionObject = mapValue(suggestionItem);
            RevisionSuggestionView suggestionView = new RevisionSuggestionView(
                    JsonValues.string(suggestionObject, "priority"),
                    JsonValues.string(suggestionObject, "target"),
                    JsonValues.string(suggestionObject, "instruction"),
                    JsonValues.string(suggestionObject, "rationale")
            );
            if (suggestionView.priority().isBlank()
                    || suggestionView.target().isBlank()
                    || suggestionView.instruction().isBlank()
                    || suggestionView.rationale().isBlank()) {
                throw new IllegalStateException("revisionSuggestions must contain priority, target, instruction and rationale");
            }
            revisionSuggestions.add(suggestionView);
        }

        boolean needsRevision = JsonValues.booleanValue(jsonObject, "needsRevision");
        if (!needsRevision && !revisionSuggestions.isEmpty()) {
            throw new IllegalStateException("revisionSuggestions must be empty when needsRevision is false");
        }
        boolean revisionAllowed = command.currentRevisionRound() < command.maxRevisionRounds();
        return new QualityReportView(
                integerOrZero(jsonObject, "score"),
                toStringList(JsonValues.list(jsonObject, "passes")),
                issues,
                needsRevision,
                toStringList(JsonValues.list(jsonObject, "riskFlags")),
                revisionSuggestions,
                command.currentRevisionRound(),
                command.maxRevisionRounds(),
                revisionAllowed,
                reviewSummary
        );
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private int integerOrZero(Map<String, Object> values, String key) {
        Integer value = JsonValues.integerValue(values, key);
        return value == null ? 0 : value;
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

    private boolean isSparseIdentifierOnlyRequest(QualityReviewCommand command) {
        return command != null
                && command.draftText().isBlank()
                && isBlankList(command.userRequirements())
                && isBlankList(command.personaProfile())
                && isBlankList(command.storyOutline())
                && isBlankList(command.timelineConstraints())
                && isBlankList(command.worldRules())
                && isBlankList(command.characterKnowledgeBoundaries());
    }

    private boolean isBlankList(List<String> values) {
        return values == null || values.stream().noneMatch(value -> value != null && !value.isBlank());
    }

    private QualityReviewCommand enrichCommand(QualityReviewCommand command, ToolCallRequest request) {
        String resolvedDraftText = resolveDraftText(command, request);
        return new QualityReviewCommand(
                resolvedDraftText,
                defaultIfEmpty(command == null ? null : command.userRequirements(), "Review against the current run request."),
                defaultIfEmpty(command == null ? null : command.personaProfile(), "Review character consistency against available context."),
                defaultIfEmpty(command == null ? null : command.storyOutline(), "Review plot logic against available context."),
                defaultIfEmpty(command == null ? null : command.timelineConstraints(), "Review timeline consistency against available context."),
                defaultIfEmpty(command == null ? null : command.worldRules(), "Review world rules against available context."),
                defaultIfEmpty(command == null ? null : command.characterKnowledgeBoundaries(), "Review character knowledge boundaries against available context."),
                command == null ? 0 : command.currentRevisionRound(),
                command == null ? 0 : command.maxRevisionRounds()
        );
    }

    private String resolveDraftText(QualityReviewCommand command, ToolCallRequest request) {
        String draftText = command == null ? null : command.draftText();
        if (draftText != null && !draftText.isBlank()) {
            return draftText.trim();
        }
        Long chapterId = extractLong(request == null ? null : request.toolArgsJson(), "chapterId");
        if (request != null && request.projectId() != null && chapterId != null && novelApplicationService != null) {
            String chapterContent = novelApplicationService.getChapterContentText(request.projectId(), chapterId);
            if (chapterContent != null && !chapterContent.isBlank()) {
                return chapterContent.trim();
            }
        }
        return firstNonBlank(extractString(request == null ? null : request.contextJson(), "selectedText"), "No draft text was provided for this run.");
    }

    private List<String> defaultIfEmpty(List<String> values, String fallback) {
        if (values != null && values.stream().anyMatch(value -> value != null && !value.isBlank())) {
            return values;
        }
        return List.of(firstNonBlank(fallback, "Not provided."));
    }

    private Long extractLong(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonValues.longValue(jsonCodec.readObject(json), fieldName);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractString(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonValues.nullableString(jsonCodec.readObject(json), fieldName);
        } catch (Exception ex) {
            return null;
        }
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
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
        builder.append("如果发现问题，不要只写质量良好，必须提供结构化 issues 列表与 revisionSuggestions。\n");
        return builder.toString().trim();
    }

    private void appendSection(StringBuilder builder, String title, List<String> items) {
        builder.append(title).append(":\n");
        for (String item : items) {
            builder.append("- ").append(item == null ? "" : item).append("\n");
        }
    }
}
