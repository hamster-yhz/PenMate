package com.penmate.backend.application.agent.tool;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.support.QualityReviewCommandParser;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QualityReviewApplicationServiceTest {

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_RETURN_STRUCTURED_REPORT_AND_BUILD_REVIEW_PROMPT() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-quality-service-1"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", revisionRequiredReportJson(), List.of(), "{}"));

        ToolCallResult result = review(service, request("call-quality-service-1", validArgsJson()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getInt("score")).isEqualTo(61);
        assertThat(output.getBool("needsRevision")).isTrue();
        assertThat(output.getBool("revisionAllowed")).isTrue();
        assertThat(output.getStr("reviewSummary")).contains("revision required");

        ArgumentCaptor<AgentLlmTurnRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway).generateTurn(requestCaptor.capture(), eq(executionConfig));
        assertThat(requestCaptor.getValue().messages().get(0).content())
                .contains("Chapter 3 draft text")
                .contains("Keep first person POV")
                .contains("Only the heroine knows the tunnel location")
                .contains("当前修订轮次：1")
                .contains("最大修订轮次：2");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_NOT_LOAD_GENERATION_TASK_FOR_STRUCTURED_RUN_REQUEST() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-quality-run-shaped"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", passReportJson(), List.of(), "{}"));

        ToolCallResult result = review(service, request("call-quality-run-shaped", validArgsJson()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        verifyNoInteractions(agentRepository);
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_ALLOW_ZERO_ISSUE_PASS_RESULT() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-quality-service-pass"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", passReportJson(), List.of(), "{}"));

        ToolCallResult result = review(service, request("call-quality-service-pass", validArgsJson()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getInt("score")).isEqualTo(96);
        assertThat(output.getBool("needsRevision")).isFalse();
        assertThat(output.getJSONArray("issues")).isEmpty();
        assertThat(output.getJSONArray("revisionSuggestions")).isEmpty();
        assertThat(output.getStr("reviewSummary")).isEqualTo("quality is acceptable");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_DISABLE_REVISION_WHEN_ROUND_LIMIT_REACHED() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-quality-service-limit"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", revisionRequiredReportJson(), List.of(), "{}"));

        ToolCallResult result = review(service, request("call-quality-service-limit", limitReachedArgsJson()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getBool("needsRevision")).isTrue();
        assertThat(output.getBool("revisionAllowed")).isFalse();
        assertThat(output.getInt("currentRevisionRound")).isEqualTo(2);
        assertThat(output.getInt("maxRevisionRounds")).isEqualTo(2);
        assertThat(output.getJSONArray("revisionSuggestions")).isNotEmpty();
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_REJECT_RESULT_WHEN_REVISION_SUGGESTIONS_MISSING() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-quality-service-no-suggestions"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", """
                        {
                          "score": 61,
                          "passes": ["main conflict exists"],
                          "issues": [
                            {
                              "dimension": "PLOT_LOGIC",
                              "severity": "HIGH",
                              "summary": "secret known too early",
                              "evidence": "paragraph two states it directly",
                              "suggestion": "change the information source"
                            }
                          ],
                          "riskFlags": ["PLOT_HOLE"],
                          "needsRevision": true,
                          "reviewSummary": "revision required"
                        }
                        """, List.of(), "{}"));

        assertThatThrownBy(() -> review(service, request("call-quality-service-no-suggestions", validArgsJson())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revisionSuggestions");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_REJECT_MALFORMED_JSON_ARGUMENTS() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentModelRoutingService, agentLlmGateway);

        assertThatThrownBy(() -> review(service, request("call-quality-service-malformed-json", "{")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolArgsJson must be valid JSON");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_REJECT_INVALID_REVISION_ROUND_ARGUMENTS() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentModelRoutingService, agentLlmGateway);

        assertThatThrownBy(() -> review(service, request("call-quality-invalid-round", """
                {
                  "draftText": "Chapter 3 draft text.",
                  "userRequirements": ["Keep first person POV"],
                  "personaProfile": ["Heroine stays calm"],
                  "storyOutline": ["Night banquet receives a secret command"],
                  "timelineConstraints": ["The full chapter happens in one night"],
                  "worldRules": ["Ordinary characters cannot cast forbidden techniques directly"],
                  "characterKnowledgeBoundaries": ["Only the heroine knows the tunnel location"],
                  "currentRevisionRound": 3,
                  "maxRevisionRounds": 2
                }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currentRevisionRound must be between 0 and maxRevisionRounds");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_REJECT_WHEN_ARRAY_ONLY_CONTAINS_BLANK_ITEMS() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentModelRoutingService, agentLlmGateway);

        assertThatThrownBy(() -> review(service, request("call-quality-blank-array-item", """
                {
                  "draftText": "Chapter 3 draft text.",
                  "userRequirements": ["   "],
                  "personaProfile": ["Heroine stays calm"],
                  "storyOutline": ["Night banquet receives a secret command"],
                  "timelineConstraints": ["The full chapter happens in one night"],
                  "worldRules": ["Ordinary characters cannot cast forbidden techniques directly"],
                  "characterKnowledgeBoundaries": ["Only the heroine knows the tunnel location"],
                  "currentRevisionRound": 0,
                  "maxRevisionRounds": 2
                }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userRequirements must contain at least one non-blank item");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_ACCEPT_IDENTIFIER_ONLY_ARGS_AND_USE_RUN_CONTEXT_DEFAULTS() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object service = instantiateQualityReviewApplicationService(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();
        java.util.concurrent.atomic.AtomicReference<ToolCallResult> resultRef = new java.util.concurrent.atomic.AtomicReference<>();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-quality-identifiers-only"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", passReportJson(), List.of(), "{}"));

        assertThatCode(() -> resultRef.set(review(service, request("call-quality-identifiers-only", """
                {
                  "chapterId": 5001,
                  "draftId": 3001,
                  "currentRevisionRound": 1,
                  "maxRevisionRounds": 2
                }
                """))))
                .doesNotThrowAnyException();
        assertThat(resultRef.get()).isNotNull();
        assertThat(resultRef.get().status()).isEqualTo("SUCCESS");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_APPLICATION_SERVICE_REVIEW_SHOULD_LOAD_REAL_CHAPTER_CONTENT_FROM_NOVEL_SERVICE_WHEN_ONLY_CHAPTER_IDENTIFIER_PROVIDED() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        NovelApplicationService novelApplicationService = mock(NovelApplicationService.class);
        Object service = instantiateQualityReviewApplicationService(agentModelRoutingService, agentLlmGateway, novelApplicationService);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(novelApplicationService.getChapterContentText(9001L, 5001L))
                .thenReturn("Real chapter content: the chase stops at the alley.");
        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-quality-chapter-content"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", passReportJson(), List.of(), "{}"));

        ToolCallResult result = review(service, request("call-quality-chapter-content", """
                {
                  "chapterId": 5001,
                  "currentRevisionRound": 0,
                  "maxRevisionRounds": 2
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        ArgumentCaptor<AgentLlmTurnRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway).generateTurn(requestCaptor.capture(), eq(executionConfig));
        verify(novelApplicationService).getChapterContentText(9001L, 5001L);
        assertThat(requestCaptor.getValue().messages().get(0).content())
                .contains("Real chapter content: the chase stops at the alley.");
    }

    private static Object instantiateQualityReviewApplicationService(AgentModelRoutingService agentModelRoutingService,
                                                                     AgentLlmGateway agentLlmGateway) throws Exception {
        Class<?> clazz = loadClass("com.penmate.backend.application.agent.tool.DefaultQualityReviewApplicationService");
        Constructor<?> constructor = clazz.getDeclaredConstructor(
                AgentModelRoutingService.class,
                AgentLlmGateway.class,
                QualityReviewCommandParser.class,
                com.penmate.backend.application.common.serialization.JsonCodec.class
        );
        constructor.setAccessible(true);
        JacksonJsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
        return constructor.newInstance(
                agentModelRoutingService,
                agentLlmGateway,
                new QualityReviewCommandParser(jsonCodec),
                jsonCodec
        );
    }

    private static Object instantiateQualityReviewApplicationService(AgentModelRoutingService agentModelRoutingService,
                                                                     AgentLlmGateway agentLlmGateway,
                                                                     NovelApplicationService novelApplicationService) throws Exception {
        Class<?> clazz = loadClass("com.penmate.backend.application.agent.tool.DefaultQualityReviewApplicationService");
        Constructor<?> constructor = clazz.getDeclaredConstructor(
                AgentModelRoutingService.class,
                AgentLlmGateway.class,
                QualityReviewCommandParser.class,
                NovelApplicationService.class,
                com.penmate.backend.application.common.serialization.JsonCodec.class
        );
        constructor.setAccessible(true);
        JacksonJsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
        return constructor.newInstance(
                agentModelRoutingService,
                agentLlmGateway,
                new QualityReviewCommandParser(jsonCodec),
                novelApplicationService,
                jsonCodec
        );
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

    private static String validArgsJson() {
        return """
                {
                  "draftText": "Chapter 3 draft text.",
                  "userRequirements": ["Keep first person POV", "Maintain suspense"],
                  "personaProfile": ["Heroine stays calm"],
                  "storyOutline": ["Night banquet receives a secret command"],
                  "timelineConstraints": ["The full chapter happens in one night"],
                  "worldRules": ["Ordinary characters cannot cast forbidden techniques directly"],
                  "characterKnowledgeBoundaries": ["Only the heroine knows the tunnel location"],
                  "currentRevisionRound": 1,
                  "maxRevisionRounds": 2
                }
                """;
    }

    private static String limitReachedArgsJson() {
        return """
                {
                  "draftText": "Chapter 3 revised draft.",
                  "userRequirements": ["Keep first person POV", "Maintain suspense"],
                  "personaProfile": ["Heroine stays calm"],
                  "storyOutline": ["Night banquet receives a secret command"],
                  "timelineConstraints": ["The full chapter happens in one night"],
                  "worldRules": ["Ordinary characters cannot cast forbidden techniques directly"],
                  "characterKnowledgeBoundaries": ["Only the heroine knows the tunnel location"],
                  "currentRevisionRound": 2,
                  "maxRevisionRounds": 2
                }
                """;
    }

    private static String passReportJson() {
        return """
                {
                  "score": 96,
                  "passes": ["requirements satisfied", "timeline consistent"],
                  "issues": [],
                  "riskFlags": [],
                  "needsRevision": false,
                  "revisionSuggestions": [],
                  "reviewSummary": "quality is acceptable"
                }
                """;
    }

    private static String revisionRequiredReportJson() {
        return """
                {
                  "score": 61,
                  "passes": ["main conflict exists"],
                  "issues": [
                    {
                      "dimension": "PLOT_LOGIC",
                      "severity": "HIGH",
                      "summary": "protagonist knows the secret command too early",
                      "evidence": "paragraph two states the command directly",
                      "suggestion": "add a valid intelligence source"
                    }
                  ],
                  "riskFlags": ["PLOT_HOLE"],
                  "needsRevision": true,
                  "revisionSuggestions": [
                    {
                      "priority": "P0",
                      "target": "plot logic",
                      "instruction": "repair the secret command source and tunnel knowledge boundary",
                      "rationale": "the conflict currently depends on invalid knowledge"
                    }
                  ],
                  "reviewSummary": "revision required for plot logic"
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
