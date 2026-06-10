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
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TodoPlannerToolHandlerTest {

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_DEFINITION_SHOULD_EXPOSE_SCHEMA_DISPLAY_NAME_AND_GOVERNANCE_POLICY() throws Exception {
        Object definition = instantiateTodoPlannerToolDefinition();
        Object descriptor = definition.getClass().getMethod("descriptor").invoke(definition);

        assertThat(readAccessor(descriptor, "toolCode")).isEqualTo("todo_planner");

        Object presentation = readAccessor(descriptor, "presentation");
        assertThat(readAccessor(presentation, "displayName")).isEqualTo("Todo 规划");

        Object exposure = readAccessor(descriptor, "exposure");
        assertThat(readAccessor(exposure, "exposedToLlm")).isEqualTo(true);
        String schema = String.valueOf(readAccessor(exposure, "parametersJsonSchema"));
        assertThat(schema)
                .contains("\"planningMode\"")
                .contains("TASK_BREAKDOWN")
                .contains("QUALITY_REMEDIATION")
                .contains("FOLLOW_UP_MODIFICATION")
                .contains("\"userRequest\"")
                .contains("\"qualityIssues\"")
                .contains("\"severity\"")
                .contains("\"summary\"")
                .contains("\"suggestion\"")
                .contains("\"storyBibleUpdates\"")
                .contains("\"planningContext\"")
                .contains("\"existingTodos\"")
                .contains("\"required\": [\"planningMode\"]")
                .contains("\"oneOf\"")
                .contains("\"const\": \"QUALITY_REMEDIATION\"")
                .contains("\"required\": [\"planningMode\", \"qualityIssues\"]")
                .contains("\"additionalProperties\": false");

        Object governancePolicy = readAccessor(descriptor, "governancePolicy");
        assertThat(readAccessor(governancePolicy, "riskLevel")).isEqualTo(1);
        Object defaultDecision = readAccessor(governancePolicy, "defaultDecision");
        assertThat(readAccessor(defaultDecision, "approvalRequired")).isEqualTo(false);
        Map<?, ?> operationPolicies = castMap(readAccessor(governancePolicy, "operationPolicies"));
        assertThat(operationPolicies).isEmpty();
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_RETURN_CARD_READY_TODO_PLAN_WITH_RUN_SHAPED_CONTEXT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-todo-1"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", """
                        {
                          "planTitle": "Chapter 3 revision plan",
                          "planSummary": "Group user requests, quality fixes, story bible updates, and planning work into todo cards.",
                          "recommendedNextAction": "Fix P0 continuity issues first, then sync story bible updates.",
                          "items": [
                            {
                              "title": "Break down chapter 3 revision",
                              "description": "Split the requested rewrite into continuity, pacing, and prose tasks.",
                              "priority": "P0",
                              "sourceType": "USER_REQUEST",
                              "recommendedStatus": "TODO",
                              "suggestedAutoCreate": true,
                              "rationale": "This is the user's direct delivery request.",
                              "acceptanceCriteria": ["Three subtasks exist", "Each subtask has a concrete output"],
                              "dependsOn": []
                            },
                            {
                              "title": "Fix early secret knowledge",
                              "description": "Remove character knowledge that should not be available yet.",
                              "priority": "P0",
                              "sourceType": "QUALITY_REVIEW",
                              "recommendedStatus": "TODO",
                              "suggestedAutoCreate": true,
                              "rationale": "The issue breaks story continuity.",
                              "acceptanceCriteria": ["Invalid knowledge is removed", "A valid source is added"],
                              "dependsOn": ["Break down chapter 3 revision"]
                            },
                            {
                              "title": "Sync tunnel boundary rule",
                              "description": "Add the tunnel knowledge boundary to the story bible update list.",
                              "priority": "P1",
                              "sourceType": "STORY_BIBLE_UPDATE",
                              "recommendedStatus": "TODO",
                              "suggestedAutoCreate": false,
                              "rationale": "The setting must remain consistent in later chapters.",
                              "acceptanceCriteria": ["Boundary rule is captured", "No existing rule conflicts"],
                              "dependsOn": ["Fix early secret knowledge"]
                            },
                            {
                              "title": "Schedule review after revision",
                              "description": "Run another review after the blocking fixes are complete.",
                              "priority": "P2",
                              "sourceType": "PLANNING",
                              "recommendedStatus": "BLOCKED",
                              "suggestedAutoCreate": false,
                              "rationale": "The review depends on earlier fixes.",
                              "acceptanceCriteria": ["Review input is ready"],
                              "dependsOn": ["Fix early secret knowledge", "Sync tunnel boundary rule"]
                            }
                          ]
                        }
                        """, List.of(), "{}"));

        ToolCallResult result = execute(handler, request("call-todo-1", validArgsJson()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getStr("planTitle")).isEqualTo("Chapter 3 revision plan");
        assertThat(output.getStr("planSummary")).isNotBlank();
        assertThat(output.getStr("recommendedNextAction")).isNotBlank();

        JSONArray items = output.getJSONArray("items");
        assertThat(items).isNotNull();
        assertThat(items).hasSize(4);
        List<String> sourceTypes = items.toList(JSONObject.class).stream()
                .map(item -> ((JSONObject) item).getStr("sourceType"))
                .collect(Collectors.toList());
        assertThat(sourceTypes)
                .containsExactlyInAnyOrder("USER_REQUEST", "QUALITY_REVIEW", "STORY_BIBLE_UPDATE", "PLANNING");

        JSONObject firstItem = items.getJSONObject(0);
        assertThat(firstItem.getStr("title")).isNotBlank();
        assertThat(firstItem.getStr("description")).isNotBlank();
        assertThat(firstItem.getStr("priority")).isEqualTo("P0");
        assertThat(firstItem.getStr("recommendedStatus")).isEqualTo("TODO");
        assertThat(firstItem.getBool("suggestedAutoCreate")).isTrue();
        assertThat(firstItem.getStr("rationale")).isNotBlank();
        assertThat(firstItem.getJSONArray("acceptanceCriteria").toList(String.class)).isNotEmpty();
        assertThat(firstItem.getJSONArray("dependsOn").toList(String.class)).isEmpty();

        ArgumentCaptor<AgentLlmTurnRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway).generateTurn(requestCaptor.capture(), eq(executionConfig));
        assertThat(requestCaptor.getValue().messages().get(0).content())
                .contains("FOLLOW_UP_MODIFICATION")
                .contains("rewrite chapter 3 and fix night banquet continuity")
                .contains("protagonist knows the secret command too early")
                .contains("tunnel location is known only by the heroine")
                .contains("current todos include relationship consistency checks")
                .contains("只输出 Todo 规划建议")
                .contains("不要直接创建或持久化 todo");

        verifyNoInteractions(agentRepository);
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_MAP_PROVIDER_EXCEPTION_TO_STABLE_FAILED_RESULT() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-todo-provider-failed"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenThrow(new RuntimeException("provider timeout"));

        ToolCallResult result = execute(handler, request("call-todo-provider-failed", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TODO_PLANNER_FAILED");
        assertThat(result.errorMessage()).isEqualTo("provider timeout");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_REJECT_BULLET_STRING_RESPONSE_WITHOUT_STRUCTURED_ITEMS() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-todo-bullets"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", "- Fix chapter 3\n- Update story bible", List.of(), "{}"));

        ToolCallResult result = execute(handler, request("call-todo-bullets", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TODO_PLANNER_FAILED");
        assertThat(result.errorMessage()).contains("todo planner result must");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_UNSUPPORTED_PLANNING_MODE() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentModelRoutingService, agentLlmGateway);
        Method validateMethod = handler.getClass().getMethod("validate", ToolCallRequest.class);

        assertThatThrownBy(() -> {
            try {
                validateMethod.invoke(handler, request("call-todo-invalid-mode", """
                        {
                          "planningMode": "RETROFIT",
                          "userRequest": "rewrite chapter 3",
                          "qualityIssues": [
                            {
                              "severity": "HIGH",
                              "summary": "protagonist knows the secret command too early",
                              "suggestion": "add a valid intelligence source"
                            }
                          ],
                          "storyBibleUpdates": ["tunnel location is known only by the heroine"],
                          "planningContext": ["chapter 3 draft exists"],
                          "existingTodos": ["current todos include relationship consistency checks"]
                        }
                        """));
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("planningMode must be one of");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_REJECT_INVALID_PRIORITY_VALUE() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-todo-invalid-priority"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", invalidPlanJson("""
                        "priority": "URGENT",
                        "sourceType": "QUALITY_REVIEW",
                        "recommendedStatus": "TODO"
                        """), List.of(), "{}"));

        ToolCallResult result = execute(handler, request("call-todo-invalid-priority", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TODO_PLANNER_FAILED");
        assertThat(result.errorMessage()).contains("priority must be one of");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_REJECT_INVALID_RECOMMENDED_STATUS_VALUE() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-call-todo-invalid-status"))
                .thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", invalidPlanJson("""
                        "priority": "P0",
                        "sourceType": "QUALITY_REVIEW",
                        "recommendedStatus": "LATER"
                        """), List.of(), "{}"));

        ToolCallResult result = execute(handler, request("call-todo-invalid-status", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TODO_PLANNER_FAILED");
        assertThat(result.errorMessage()).contains("recommendedStatus must be one of");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_MAP_NULL_REQUEST_TO_STABLE_FAILED_RESULT() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentModelRoutingService, agentLlmGateway);

        ToolCallResult result = execute(handler, null);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TODO_PLANNER_FAILED");
        assertThat(result.errorMessage()).contains("request must not be null");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_VALIDATE_SHOULD_ALLOW_QUALITY_REMEDIATION_WITHOUT_USER_REQUEST() throws Exception {
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentModelRoutingService, agentLlmGateway);
        Method validateMethod = handler.getClass().getMethod("validate", ToolCallRequest.class);

        try {
            validateMethod.invoke(handler, request("call-todo-quality-only", """
                    {
                      "planningMode": "QUALITY_REMEDIATION",
                      "qualityIssues": [
                        {
                          "severity": "HIGH",
                          "summary": "protagonist knows the secret command too early",
                          "suggestion": "remove the early knowledge and add a valid source"
                        }
                      ],
                      "planningContext": ["needs another review after fix"]
                    }
                    """));
        } catch (InvocationTargetException ex) {
            Throwable target = ex.getTargetException();
            if (target instanceof Exception exception) {
                throw exception;
            }
            if (target instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(target);
        }
    }

    private static Object instantiateTodoPlannerToolDefinition() throws Exception {
        return instantiateNoArgsClass(
                "com.penmate.backend.application.agent.tool.definition.TodoPlannerToolDefinition"
        );
    }

    private static Object instantiateTodoPlannerToolHandler(AgentModelRoutingService agentModelRoutingService,
                                                            AgentLlmGateway agentLlmGateway) throws Exception {
        Class<?> clazz = loadClass("com.penmate.backend.application.agent.tool.handler.TodoPlannerToolHandler");
        Constructor<?> constructor = clazz.getDeclaredConstructor(
                AgentModelRoutingService.class,
                AgentLlmGateway.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(agentModelRoutingService, agentLlmGateway);
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
                "todo_planner",
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
                  "planningMode": "FOLLOW_UP_MODIFICATION",
                  "userRequest": "rewrite chapter 3 and fix night banquet continuity",
                  "qualityIssues": [
                    {
                      "severity": "HIGH",
                      "summary": "protagonist knows the secret command too early",
                      "suggestion": "remove early knowledge and add a valid intelligence source"
                    },
                    {
                      "severity": "MEDIUM",
                      "summary": "the scene switches from night to dawn unexpectedly",
                      "suggestion": "keep the scene during the night lantern sequence"
                    }
                  ],
                  "storyBibleUpdates": [
                    "tunnel location is known only by the heroine",
                    "ordinary characters cannot cast forbidden techniques directly"
                  ],
                  "planningContext": [
                    "chapter 3 draft already exists",
                    "another review is needed after this revision"
                  ],
                  "existingTodos": [
                    "current todos include relationship consistency checks"
                  ]
                }
                """;
    }

    private static String invalidPlanJson(String invalidFields) {
        return """
                {
                  "planTitle": "Chapter 3 todo plan",
                  "planSummary": "Plan revision work.",
                  "recommendedNextAction": "Fix high risk issues first.",
                  "items": [
                    {
                      "title": "Fix continuity",
                      "description": "Repair the invalid knowledge chain.",
                %s,
                      "suggestedAutoCreate": true,
                      "rationale": "The issue blocks the chapter.",
                      "acceptanceCriteria": ["Continuity is valid"],
                      "dependsOn": []
                    }
                  ]
                }
                """.formatted(invalidFields.stripIndent().trim());
    }

    private static AgentLlmExecutionConfig executionConfig() {
        return AgentLlmExecutionConfig.builder()
                .providerCode("openai-compatible")
                .modelName("gpt-test")
                .build();
    }
}
