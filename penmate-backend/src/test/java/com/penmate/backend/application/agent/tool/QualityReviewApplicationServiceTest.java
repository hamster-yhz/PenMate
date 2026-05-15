package com.penmate.backend.application.agent.tool;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.tool.support.QualityReviewCommandParser;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityReviewApplicationServiceTest {

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_RETURN_STRUCTURED_REPORT_AND_BUILD_REVIEW_PROMPT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-service-1")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("""
                        {
                          "score": 61,
                          "passes": ["用户要求主冲突已出现"],
                          "issues": [
                            {
                              "dimension": "PLOT_LOGIC",
                              "severity": "HIGH",
                              "summary": "主角在未获情报前提前得知密令内容",
                              "evidence": "第二段直接复述密令全文",
                              "suggestion": "删除提前知情描写，改为通过侍从转述获取信息"
                            }
                          ],
                          "riskFlags": ["PLOT_HOLE"],
                          "needsRevision": true,
                          "revisionSuggestions": [
                            {
                              "priority": "P0",
                              "target": "剧情逻辑",
                              "instruction": "修复密令来源与密道知情范围，避免角色越界知情",
                              "rationale": "当前冲突建立在错误前提上，会削弱可信度"
                            }
                          ],
                          "reviewSummary": "存在高风险剧情逻辑问题，需要修订后再继续生成。"
                        }
                        """);

        ToolCallResult result = review(service, request("call-quality-service-1", validArgsJson()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getInt("score")).isEqualTo(61);
        assertThat(output.getBool("needsRevision")).isTrue();
        assertThat(output.getBool("revisionAllowed")).isTrue();
        assertThat(output.getStr("reviewSummary")).contains("需要修订");

        ArgumentCaptor<AgentGenerationTask> taskCaptor = ArgumentCaptor.forClass(AgentGenerationTask.class);
        verify(agentLlmGateway).generate(taskCaptor.capture(), eq(List.of()), eq(""), eq(executionConfig));
        assertThat(taskCaptor.getValue().getPromptSnapshot())
                .contains("第三章初稿正文")
                .contains("保留第一人称")
                .contains("角色知识边界")
                .contains("当前修订轮次：1")
                .contains("最大修订轮次：2");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_ALLOW_ZERO_ISSUE_PASS_RESULT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-service-pass")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("""
                        {
                          "score": 96,
                          "passes": ["用户要求满足", "时间线一致"],
                          "issues": [],
                          "riskFlags": [],
                          "needsRevision": false,
                          "revisionSuggestions": [],
                          "reviewSummary": "质量良好"
                        }
                        """);

        ToolCallResult result = review(service, request("call-quality-service-pass", validArgsJson()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getInt("score")).isEqualTo(96);
        assertThat(output.getBool("needsRevision")).isFalse();
        assertThat(output.getJSONArray("issues")).isEmpty();
        assertThat(output.getJSONArray("revisionSuggestions")).isEmpty();
        assertThat(output.getStr("reviewSummary")).isEqualTo("质量良好");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_DISABLE_REVISION_WHEN_ROUND_LIMIT_REACHED() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-service-limit")).thenReturn(executionConfig);
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

        ToolCallResult result = review(service, request("call-quality-service-limit", """
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
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_REJECT_NON_JSON_PROVIDER_RESULT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-service-bad-json-result")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("not-json");

        assertThatThrownBy(() -> review(service, request("call-quality-service-bad-json-result", validArgsJson())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quality review result must");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_REJECT_RESULT_WHEN_REVISION_SUGGESTIONS_MISSING() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-quality-service-no-suggestions")).thenReturn(executionConfig);
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

        assertThatThrownBy(() -> review(service, request("call-quality-service-no-suggestions", validArgsJson())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revisionSuggestions");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_REJECT_MALFORMED_JSON_ARGUMENTS() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentRepository, agentModelRoutingService, agentLlmGateway);

        assertThatThrownBy(() -> review(service, request("call-quality-service-malformed-json", "{")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolArgsJson must be valid JSON");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_REJECT_INVALID_REVISION_ROUND_ARGUMENTS() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentRepository, agentModelRoutingService, agentLlmGateway);

        assertThatThrownBy(() -> review(service, request("call-quality-invalid-round", """
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
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currentRevisionRound must be between 0 and maxRevisionRounds");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_REJECT_WHEN_ARRAY_ONLY_CONTAINS_BLANK_ITEMS() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentRepository, agentModelRoutingService, agentLlmGateway);

        assertThatThrownBy(() -> review(service, request("call-quality-blank-array-item", """
                {
                  "draftText": "第三章初稿正文",
                  "userRequirements": ["   "],
                  "personaProfile": ["女主冷静克制"],
                  "storyOutline": ["夜宴收到密令"],
                  "timelineConstraints": ["全章发生于同一夜晚"],
                  "worldRules": ["凡人不可直接施法"],
                  "characterKnowledgeBoundaries": ["密道位置仅女主知晓"],
                  "currentRevisionRound": 0,
                  "maxRevisionRounds": 2
                }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userRequirements must contain at least one non-blank item");
    }

    private static Object instantiateQualityReviewApplicationService(AgentRepository agentRepository,
                                                                     AgentModelRoutingService agentModelRoutingService,
                                                                     AgentLlmGateway agentLlmGateway) throws Exception {
        Class<?> clazz = loadClass("com.penmate.backend.application.agent.tool.DefaultQualityReviewApplicationService");
        Constructor<?> constructor = clazz.getDeclaredConstructor(
                AgentRepository.class,
                AgentModelRoutingService.class,
                AgentLlmGateway.class,
                QualityReviewCommandParser.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(agentRepository, agentModelRoutingService, agentLlmGateway, new QualityReviewCommandParser());
    }

    private static Class<?> loadClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected class to exist: " + fqcn, ex);
        }
    }

    private static ToolCallResult review(Object service, ToolCallRequest request) throws Exception {
        Method reviewMethod = service.getClass().getMethod("review", ToolCallRequest.class);
        reviewMethod.setAccessible(true);
        try {
            return (ToolCallResult) reviewMethod.invoke(service, request);
        } catch (InvocationTargetException ex) {
            Throwable target = ex.getTargetException();
            if (target instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
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
