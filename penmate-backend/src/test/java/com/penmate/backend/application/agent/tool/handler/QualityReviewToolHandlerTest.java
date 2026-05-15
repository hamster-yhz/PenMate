package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityReviewToolHandlerTest {

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_DEFINITION_SHOULD_EXPOSE_SCHEMA_DISPLAY_NAME_AND_GOVERNANCE_POLICY() throws Exception {
        Object definition = instantiateQualityReviewToolDefinition();
        Object descriptor = definition.getClass().getMethod("descriptor").invoke(definition);

        assertThat(readAccessor(descriptor, "toolCode")).isEqualTo("quality_review");

        Object presentation = readAccessor(descriptor, "presentation");
        assertThat(readAccessor(presentation, "displayName")).isEqualTo("质量审查");

        Object exposure = readAccessor(descriptor, "exposure");
        assertThat(readAccessor(exposure, "exposedToLlm")).isEqualTo(true);
        String schema = String.valueOf(readAccessor(exposure, "parametersJsonSchema"));
        assertThat(schema)
                .contains("\"draftText\"")
                .contains("\"userRequirements\"")
                .contains("\"personaProfile\"")
                .contains("\"storyOutline\"")
                .contains("\"timelineConstraints\"")
                .contains("\"worldRules\"")
                .contains("\"characterKnowledgeBoundaries\"")
                .contains("\"currentRevisionRound\"")
                .contains("\"maxRevisionRounds\"")
                .contains("\"required\": [\"draftText\"")
                .contains("\"additionalProperties\": false");

        Object governancePolicy = readAccessor(descriptor, "governancePolicy");
        assertThat(readAccessor(governancePolicy, "riskLevel")).isEqualTo(1);
        Object defaultDecision = readAccessor(governancePolicy, "defaultDecision");
        assertThat(readAccessor(defaultDecision, "approvalRequired")).isEqualTo(false);
        Map<?, ?> operationPolicies = castMap(readAccessor(governancePolicy, "operationPolicies"));
        assertThat(operationPolicies).isEmpty();
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_EXECUTE_SHOULD_RETURN_STRUCTURED_REPORT_WITH_ISSUES_AND_REVISION_SUGGESTIONS() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateQualityReviewToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-1")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("""
                        {
                          "score": 61,
                          "passes": [
                            "用户要求主冲突已出现",
                            "角色语气基本匹配女主冷静设定"
                          ],
                          "issues": [
                            {
                              "dimension": "PLOT_LOGIC",
                              "severity": "HIGH",
                              "summary": "主角在未获情报前提前得知密令内容",
                              "evidence": "第二段直接复述密令全文",
                              "suggestion": "删除提前知情描写，改为通过侍从转述获取信息"
                            },
                            {
                              "dimension": "TIMELINE",
                              "severity": "MEDIUM",
                              "summary": "夜宴结束后又出现黄昏景象",
                              "evidence": "末段写到夕阳照进大殿",
                              "suggestion": "统一为夜间灯火场景"
                            },
                            {
                              "dimension": "WORLD_RULES",
                              "severity": "MEDIUM",
                              "summary": "凡人角色直接施放禁术违反世界观",
                              "evidence": "侍卫凭空引燃符阵",
                              "suggestion": "改为借助预置机关或法器触发"
                            },
                            {
                              "dimension": "CHARACTER_KNOWLEDGE_BOUNDARY",
                              "severity": "HIGH",
                              "summary": "配角知道仅女主掌握的密道位置",
                              "evidence": "副官直接指出密道入口",
                              "suggestion": "补充情报来源或改为女主带路"
                            }
                          ],
                          "riskFlags": ["PLOT_HOLE", "KNOWLEDGE_LEAK"],
                          "needsRevision": true,
                          "revisionSuggestions": [
                            {
                              "priority": "P0",
                              "target": "剧情逻辑",
                              "instruction": "修复密令来源与密道知情范围，避免角色越界知情",
                              "rationale": "当前冲突建立在错误前提上，会削弱可信度"
                            },
                            {
                              "priority": "P1",
                              "target": "时间线与世界观",
                              "instruction": "统一夜宴时间表现，并把禁术改写为法器触发",
                              "rationale": "避免时间跳变与世界规则冲突"
                            }
                          ],
                          "reviewSummary": "存在高风险剧情逻辑与知识边界问题，需要修订后再继续生成。"
                        }
                        """);

        ToolCallResult result = execute(handler, request("call-quality-1", """
                {
                  "draftText": "第三章初稿正文",
                  "userRequirements": ["保留第一人称", "维持紧张悬疑节奏"],
                  "personaProfile": ["女主冷静克制", "副官不掌握密道"],
                  "storyOutline": ["夜宴收到密令", "女主独自调查密道"],
                  "timelineConstraints": ["全章发生于同一夜晚"],
                  "worldRules": ["凡人不可直接施法", "禁术只能由祭司发动"],
                  "characterKnowledgeBoundaries": ["密道位置仅女主知晓", "密令全文仅皇帝与女主知晓"],
                  "currentRevisionRound": 1,
                  "maxRevisionRounds": 2
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getInt("score")).isEqualTo(61);
        assertThat(output.getBool("needsRevision")).isTrue();
        assertThat(output.getInt("currentRevisionRound")).isEqualTo(1);
        assertThat(output.getInt("maxRevisionRounds")).isEqualTo(2);
        assertThat(output.getBool("revisionAllowed")).isTrue();
        assertThat(output.getStr("reviewSummary"))
                .isNotBlank()
                .isNotEqualTo("质量良好");

        JSONArray passes = output.getJSONArray("passes");
        assertThat(passes).isNotNull();
        assertThat(passes.toList(String.class))
                .contains("用户要求主冲突已出现", "角色语气基本匹配女主冷静设定");

        JSONArray issues = output.getJSONArray("issues");
        assertThat(issues).isNotNull();
        assertThat(issues.size()).isGreaterThanOrEqualTo(4);
        assertThat(issues.toString())
                .contains("PLOT_LOGIC")
                .contains("TIMELINE")
                .contains("WORLD_RULES")
                .contains("CHARACTER_KNOWLEDGE_BOUNDARY");
        JSONObject firstIssue = issues.getJSONObject(0);
        assertThat(firstIssue.getStr("dimension")).isNotBlank();
        assertThat(firstIssue.getStr("severity")).isNotBlank();
        assertThat(firstIssue.getStr("summary")).isNotBlank();
        assertThat(firstIssue.getStr("evidence")).isNotBlank();
        assertThat(firstIssue.getStr("suggestion")).isNotBlank();

        JSONArray revisionSuggestions = output.getJSONArray("revisionSuggestions");
        assertThat(revisionSuggestions).isNotNull();
        assertThat(revisionSuggestions).hasSizeGreaterThanOrEqualTo(2);
        JSONObject firstSuggestion = revisionSuggestions.getJSONObject(0);
        assertThat(firstSuggestion.getStr("priority")).isEqualTo("P0");
        assertThat(firstSuggestion.getStr("target")).isNotBlank();
        assertThat(firstSuggestion.getStr("instruction")).isNotBlank();
        assertThat(firstSuggestion.getStr("rationale")).isNotBlank();

        ArgumentCaptor<AgentGenerationTask> taskCaptor = ArgumentCaptor.forClass(AgentGenerationTask.class);
        verify(agentLlmGateway).generate(taskCaptor.capture(), eq(List.of()), eq(""), eq(executionConfig));
        assertThat(taskCaptor.getValue().getPromptSnapshot())
                .contains("第三章初稿正文")
                .contains("保留第一人称")
                .contains("女主冷静克制")
                .contains("夜宴收到密令")
                .contains("全章发生于同一夜晚")
                .contains("凡人不可直接施法")
                .contains("密道位置仅女主知晓")
                .contains("当前修订轮次：1")
                .contains("最大修订轮次：2");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_EXECUTE_SHOULD_DISABLE_REVISION_WHEN_ROUND_LIMIT_REACHED() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateQualityReviewToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-2")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("""
                        {
                          "score": 52,
                          "passes": ["人设基调大体一致"],
                          "issues": [
                            {
                              "dimension": "USER_REQUIREMENT",
                              "severity": "HIGH",
                              "summary": "用户要求的悬疑节奏中段掉线",
                              "evidence": "中段大段说明背景没有推进冲突",
                              "suggestion": "压缩背景说明并增加现场动作"
                            }
                          ],
                          "riskFlags": ["PACE_DROP"],
                          "needsRevision": true,
                          "revisionSuggestions": [
                            {
                              "priority": "P0",
                              "target": "用户要求",
                              "instruction": "恢复悬疑节奏并删减解释段落",
                              "rationale": "已经达到修订轮次上限，需由主编排决定是否继续"
                            }
                          ],
                          "reviewSummary": "仍需修订，但本轮已达到自动修订上限。"
                        }
                        """);

        ToolCallResult result = execute(handler, request("call-quality-2", """
                {
                  "draftText": "第三章修订稿",
                  "userRequirements": ["保留第一人称", "维持紧张悬疑节奏"],
                  "personaProfile": ["女主冷静克制"],
                  "storyOutline": ["夜宴收到密令", "女主独自调查密道"],
                  "timelineConstraints": ["全章发生于同一夜晚"],
                  "worldRules": ["凡人不可直接施法"],
                  "characterKnowledgeBoundaries": ["密道位置仅女主知晓"],
                  "currentRevisionRound": 2,
                  "maxRevisionRounds": 2
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getBool("needsRevision")).isTrue();
        assertThat(output.getBool("revisionAllowed")).isFalse();
        assertThat(output.getInt("currentRevisionRound")).isEqualTo(2);
        assertThat(output.getInt("maxRevisionRounds")).isEqualTo(2);
        assertThat(output.getJSONArray("revisionSuggestions")).isNotNull();
        assertThat(output.getJSONArray("revisionSuggestions").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_EXECUTE_SHOULD_MAP_PROVIDER_EXCEPTION_TO_STABLE_FAILED_RESULT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateQualityReviewToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-3")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenThrow(new RuntimeException("provider timeout"));

        ToolCallResult result = execute(handler, request("call-quality-3", """
                {
                  "draftText": "失败样例正文",
                  "userRequirements": ["保留第一人称"],
                  "personaProfile": ["女主冷静克制"],
                  "storyOutline": ["夜宴收到密令"],
                  "timelineConstraints": ["全章发生于同一夜晚"],
                  "worldRules": ["凡人不可直接施法"],
                  "characterKnowledgeBoundaries": ["密道位置仅女主知晓"],
                  "currentRevisionRound": 0,
                  "maxRevisionRounds": 2
                }
                """));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("QUALITY_REVIEW_FAILED");
        assertThat(result.errorMessage()).isEqualTo("provider timeout");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_EXECUTE_SHOULD_MAP_NON_JSON_PROVIDER_RESULT_TO_STABLE_FAILED_RESULT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateQualityReviewToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-bad-json")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("not-json");

        ToolCallResult result = execute(handler, request("call-quality-bad-json", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("QUALITY_REVIEW_FAILED");
        assertThat(result.errorMessage()).contains("quality review result must");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_EXECUTE_SHOULD_REJECT_SUMMARY_ONLY_RESPONSE_WITHOUT_STRUCTURED_ISSUES() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateQualityReviewToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-summary-only")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("""
                        {
                          "score": 95,
                          "passes": ["整体不错"],
                          "issues": [],
                          "riskFlags": [],
                          "needsRevision": false,
                          "revisionSuggestions": [],
                          "reviewSummary": "质量良好"
                        }
                        """);

        ToolCallResult result = execute(handler, request("call-quality-summary-only", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("QUALITY_REVIEW_FAILED");
        assertThat(result.errorMessage()).contains("structured issues");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_EXECUTE_SHOULD_REJECT_RESULT_WHEN_REVISION_SUGGESTIONS_MISSING() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateQualityReviewToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-no-suggestions")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("""
                        {
                          "score": 61,
                          "passes": ["用户要求主冲突已出现"],
                          "issues": [
                            {
                              "dimension": "PLOT_LOGIC",
                              "severity": "HIGH",
                              "summary": "主角提前知道密令",
                              "evidence": "第二段直接复述密令",
                              "suggestion": "改为侍从转述"
                            }
                          ],
                          "riskFlags": ["PLOT_HOLE"],
                          "needsRevision": true,
                          "reviewSummary": "存在剧情逻辑问题，需要修订。"
                        }
                        """);

        ToolCallResult result = execute(handler, request("call-quality-no-suggestions", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("QUALITY_REVIEW_FAILED");
        assertThat(result.errorMessage()).contains("revisionSuggestions");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_INVALID_REVISION_ROUND_ARGUMENTS() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateQualityReviewToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        Method validateMethod = handler.getClass().getMethod("validate", ToolCallRequest.class);

        assertThatThrownBy(() -> {
            try {
                validateMethod.invoke(handler, request("call-quality-invalid", """
                        {
                          "draftText": "第三章初稿正文",
                          "userRequirements": ["保留第一人称"],
                          "personaProfile": ["女主冷静克制"],
                          "storyOutline": ["夜宴收到密令"],
                          "timelineConstraints": ["全章发生于同一夜晚"],
                          "worldRules": ["凡人不可直接施法"],
                          "characterKnowledgeBoundaries": ["密道位置仅女主知晓"],
                          "currentRevisionRound": 3,
                          "maxRevisionRounds": 2
                        }
                        """));
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currentRevisionRound must be between 0 and maxRevisionRounds");
    }

    private static Object instantiateQualityReviewToolDefinition() throws Exception {
        return instantiateNoArgsClass(
                "com.penmate.backend.application.agent.tool.definition.QualityReviewToolDefinition"
        );
    }

    private static Object instantiateQualityReviewToolHandler(AgentRepository agentRepository,
                                                              AgentModelRoutingService agentModelRoutingService,
                                                              AgentLlmGateway agentLlmGateway) throws Exception {
        Class<?> clazz = loadClass("com.penmate.backend.application.agent.tool.handler.QualityReviewToolHandler");
        Constructor<?> constructor = clazz.getDeclaredConstructor(
                AgentRepository.class,
                AgentModelRoutingService.class,
                AgentLlmGateway.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(agentRepository, agentModelRoutingService, agentLlmGateway);
    }

    private static Object instantiateNoArgsClass(String fqcn) throws Exception {
        Class<?> clazz = loadClass(fqcn);
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Class<?> loadClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected class to exist: " + fqcn, ex);
        }
    }

    private static Object readAccessor(Object target, String accessor) throws Exception {
        Method method = target.getClass().getMethod(accessor);
        method.setAccessible(true);
        return method.invoke(target);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> castMap(Object value) {
        return (Map<?, ?>) value;
    }

    private static ToolCallResult execute(Object handler, ToolCallRequest request) throws Exception {
        Method executeMethod = handler.getClass().getMethod("execute", ToolCallRequest.class);
        executeMethod.setAccessible(true);
        return (ToolCallResult) executeMethod.invoke(handler, request);
    }

    private static ToolCallRequest request(String toolCallId, String toolArgsJson) {
        return new ToolCallRequest(
                9001L,
                8001L,
                6001L,
                "quality_review",
                toolArgsJson,
                1001L,
                "trace-" + toolCallId,
                "{}",
                toolCallId + "-8001",
                "trace-" + toolCallId + "-loop",
                0,
                toolCallId,
                "[]",
                "[]",
                "RESUME_LOOP",
                null
        );
    }

    private static AgentGenerationTask generationTask() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(1L);
        task.setTaskId(8001L);
        task.setProjectId(9001L);
        task.setUserId(1001L);
        task.setConversationId(6001L);
        task.setChapterId(5001L);
        task.setModelConfigId(7001L);
        task.setTaskType("CHAPTER_DRAFT");
        task.setTraceId("trace-quality-seed");
        task.setPromptSnapshot("请生成第三章初稿");
        task.setPluginSnapshot("[]");
        return task;
    }

    private static String validArgsJson() {
        return """
                {
                  "draftText": "第三章初稿正文",
                  "userRequirements": ["保留第一人称", "维持紧张悬疑节奏"],
                  "personaProfile": ["女主冷静克制", "副官不掌握密道"],
                  "storyOutline": ["夜宴收到密令", "女主独自调查密道"],
                  "timelineConstraints": ["全章发生于同一夜晚"],
                  "worldRules": ["凡人不可直接施法", "禁术只能由祭司发动"],
                  "characterKnowledgeBoundaries": ["密道位置仅女主知晓", "密令全文仅皇帝与女主知晓"],
                  "currentRevisionRound": 1,
                  "maxRevisionRounds": 2
                }
                """;
    }

    private static AgentLlmExecutionConfig executionConfig() {
        return AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
    }
}
