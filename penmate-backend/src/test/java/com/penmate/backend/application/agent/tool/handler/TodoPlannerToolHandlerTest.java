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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_RETURN_CARD_READY_TODO_PLAN_WITH_ALL_SOURCE_TYPES_AND_WITHOUT_PERSISTING() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-todo-1")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("""
                        {
                          "planTitle": "第三章修订与待办规划",
                          "planSummary": "将用户任务拆解、质量问题修复、设定更新和后续规划统一整理为可执行 Todo 卡片。",
                          "recommendedNextAction": "先处理 P0 逻辑漏洞，再同步设定卡，最后安排润色与复审。",
                          "items": [
                            {
                              "title": "拆解第三章修订主任务",
                              "description": "把用户要求的第三章修订拆成逻辑修复、节奏优化和语言润色三个子任务。",
                              "priority": "P0",
                              "sourceType": "USER_REQUEST",
                              "recommendedStatus": "TODO",
                              "suggestedAutoCreate": true,
                              "rationale": "这是用户直接提出的核心交付。",
                              "acceptanceCriteria": ["形成 3 个子步骤", "每步有明确产出"],
                              "dependsOn": []
                            },
                            {
                              "title": "修复密令提前知情漏洞",
                              "description": "根据质量审查结果，删除主角提前得知密令全文的描写并补足情报来源。",
                              "priority": "P0",
                              "sourceType": "QUALITY_REVIEW",
                              "recommendedStatus": "TODO",
                              "suggestedAutoCreate": true,
                              "rationale": "高风险剧情逻辑问题会直接影响章节可信度。",
                              "acceptanceCriteria": ["删除越界知情描写", "补充合理情报来源"],
                              "dependsOn": ["拆解第三章修订主任务"]
                            },
                            {
                              "title": "同步密道设定到故事圣经",
                              "description": "将密道仅女主知晓的边界规则补入后续设定更新清单，避免配角越界知情。",
                              "priority": "P1",
                              "sourceType": "STORY_BIBLE_UPDATE",
                              "recommendedStatus": "TODO",
                              "suggestedAutoCreate": false,
                              "rationale": "设定未同步会在后续章节持续复发。",
                              "acceptanceCriteria": ["新增知识边界条目", "与现有设定不冲突"],
                              "dependsOn": ["修复密令提前知情漏洞"]
                            },
                            {
                              "title": "安排修订后复审与下一轮规划",
                              "description": "在修复完成后执行一次质量复审，并决定是否进入下一轮改写。",
                              "priority": "P2",
                              "sourceType": "PLANNING",
                              "recommendedStatus": "BLOCKED",
                              "suggestedAutoCreate": false,
                              "rationale": "依赖前置修复完成后才能执行。",
                              "acceptanceCriteria": ["复审输入齐备", "明确下一轮是否继续改写"],
                              "dependsOn": ["修复密令提前知情漏洞", "同步密道设定到故事圣经"]
                            }
                          ]
                        }
                        """);

        ToolCallResult result = execute(handler, request("call-todo-1", validArgsJson()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getStr("planTitle")).isEqualTo("第三章修订与待办规划");
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

        ArgumentCaptor<AgentGenerationTask> taskCaptor = ArgumentCaptor.forClass(AgentGenerationTask.class);
        verify(agentLlmGateway).generate(taskCaptor.capture(), eq(List.of()), eq(""), eq(executionConfig));
        assertThat(taskCaptor.getValue().getPromptSnapshot())
                .contains("FOLLOW_UP_MODIFICATION")
                .contains("重写第三章并修复夜宴逻辑")
                .contains("主角提前得知密令全文")
                .contains("密道位置仅女主知晓")
                .contains("当前还有角色关系待校对")
                .contains("只输出 Todo 规划建议")
                .contains("不要直接创建或持久化 todo");

        verify(agentRepository).findGenerationTask(9001L, 8001L);
        verifyNoMoreInteractions(agentRepository);
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_MAP_PROVIDER_EXCEPTION_TO_STABLE_FAILED_RESULT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-todo-provider-failed")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenThrow(new RuntimeException("provider timeout"));

        ToolCallResult result = execute(handler, request("call-todo-provider-failed", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TODO_PLANNER_FAILED");
        assertThat(result.errorMessage()).isEqualTo("provider timeout");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_REJECT_BULLET_STRING_RESPONSE_WITHOUT_STRUCTURED_ITEMS() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-todo-bullets")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("- 修第三章逻辑\n- 更新故事圣经\n- 安排复审");

        ToolCallResult result = execute(handler, request("call-todo-bullets", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TODO_PLANNER_FAILED");
        assertThat(result.errorMessage()).contains("todo planner result must");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_UNSUPPORTED_PLANNING_MODE() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        Method validateMethod = handler.getClass().getMethod("validate", ToolCallRequest.class);

        assertThatThrownBy(() -> {
            try {
                validateMethod.invoke(handler, request("call-todo-invalid-mode", """
                        {
                          "planningMode": "RETROFIT",
                          "userRequest": "重写第三章并修复夜宴逻辑",
                          "qualityIssues": [
                            {
                              "severity": "HIGH",
                              "summary": "主角提前得知密令全文",
                              "suggestion": "补充情报来源"
                            }
                          ],
                          "storyBibleUpdates": ["密道位置仅女主知晓"],
                          "planningContext": ["当前已有第三章初稿"],
                          "existingTodos": ["当前还有角色关系待校对"]
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
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-todo-invalid-priority")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("""
                        {
                          "planTitle": "第三章修订待办",
                          "planSummary": "整理修订动作。",
                          "recommendedNextAction": "先修复高风险问题。",
                          "items": [
                            {
                              "title": "修复主角提前知情",
                              "description": "修补剧情逻辑。",
                              "priority": "URGENT",
                              "sourceType": "QUALITY_REVIEW",
                              "recommendedStatus": "TODO",
                              "suggestedAutoCreate": true,
                              "rationale": "这是高风险问题。",
                              "acceptanceCriteria": ["情报来源合理"],
                              "dependsOn": []
                            }
                          ]
                        }
                        """);

        ToolCallResult result = execute(handler, request("call-todo-invalid-priority", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TODO_PLANNER_FAILED");
        assertThat(result.errorMessage()).contains("priority must be one of");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_REJECT_INVALID_RECOMMENDED_STATUS_VALUE() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentGenerationTask task = generationTask();
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        when(agentModelRoutingService.resolveExecutionConfig(1001L, 7001L, "trace-call-todo-invalid-status")).thenReturn(executionConfig);
        when(agentLlmGateway.generate(any(AgentGenerationTask.class), eq(List.of()), eq(""), eq(executionConfig)))
                .thenReturn("""
                        {
                          "planTitle": "第三章修订待办",
                          "planSummary": "整理修订动作。",
                          "recommendedNextAction": "先修复高风险问题。",
                          "items": [
                            {
                              "title": "修复主角提前知情",
                              "description": "修补剧情逻辑。",
                              "priority": "P0",
                              "sourceType": "QUALITY_REVIEW",
                              "recommendedStatus": "LATER",
                              "suggestedAutoCreate": true,
                              "rationale": "这是高风险问题。",
                              "acceptanceCriteria": ["情报来源合理"],
                              "dependsOn": []
                            }
                          ]
                        }
                        """);

        ToolCallResult result = execute(handler, request("call-todo-invalid-status", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TODO_PLANNER_FAILED");
        assertThat(result.errorMessage()).contains("recommendedStatus must be one of");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_EXECUTE_SHOULD_MAP_NULL_REQUEST_TO_STABLE_FAILED_RESULT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);

        ToolCallResult result = execute(handler, null);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("TODO_PLANNER_FAILED");
        assertThat(result.errorMessage()).contains("request must not be null");
    }

    @Test
    void UT_APP_AGENT_TODO_PLANNER_TOOL_HANDLER_VALIDATE_SHOULD_ALLOW_QUALITY_REMEDIATION_WITHOUT_USER_REQUEST() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateTodoPlannerToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        Method validateMethod = handler.getClass().getMethod("validate", ToolCallRequest.class);

        try {
            validateMethod.invoke(handler, request("call-todo-quality-only", """
                    {
                      "planningMode": "QUALITY_REMEDIATION",
                      "qualityIssues": [
                        {
                          "severity": "HIGH",
                          "summary": "主角提前得知密令全文",
                          "suggestion": "删除提前知情描写并补充情报来源"
                        }
                      ],
                      "planningContext": ["修复后需要复审"]
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

    private static Object instantiateTodoPlannerToolHandler(AgentRepository agentRepository,
                                                            AgentModelRoutingService agentModelRoutingService,
                                                            AgentLlmGateway agentLlmGateway) throws Exception {
        Class<?> clazz = loadClass("com.penmate.backend.application.agent.tool.handler.TodoPlannerToolHandler");
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
        task.setTraceId("trace-todo-seed");
        task.setPromptSnapshot("请修订第三章草稿");
        task.setPluginSnapshot("[]");
        return task;
    }

    private static String validArgsJson() {
        return """
                {
                  "planningMode": "FOLLOW_UP_MODIFICATION",
                  "userRequest": "重写第三章并修复夜宴逻辑",
                  "qualityIssues": [
                    {
                      "severity": "HIGH",
                      "summary": "主角提前得知密令全文",
                      "suggestion": "删除提前知情描写并补充情报来源"
                    },
                    {
                      "severity": "MEDIUM",
                      "summary": "夜宴结束后又出现黄昏景象",
                      "suggestion": "统一为夜间灯火场景"
                    }
                  ],
                  "storyBibleUpdates": [
                    "密道位置仅女主知晓",
                    "凡人不可直接施放禁术"
                  ],
                  "planningContext": [
                    "当前已有第三章初稿",
                    "本轮修订后需要安排复审"
                  ],
                  "existingTodos": [
                    "当前还有角色关系待校对"
                  ]
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
