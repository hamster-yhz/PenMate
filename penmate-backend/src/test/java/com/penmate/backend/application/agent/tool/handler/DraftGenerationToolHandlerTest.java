package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.handler.AgentToolHandler;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalView;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalViewFactory;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallExecutionService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.shared.model.ApprovalView;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DraftGenerationToolHandlerTest {

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_DEFINITION_SHOULD_EXPOSE_SCHEMA_DISPLAY_NAME_AND_GOVERNANCE_POLICY() throws Exception {
        Object definition = instantiateDraftGenerationToolDefinition();
        Object descriptor = definition.getClass().getMethod("descriptor").invoke(definition);

        assertThat(readAccessor(descriptor, "toolCode")).isEqualTo("draft_generation");

        Object presentation = readAccessor(descriptor, "presentation");
        assertThat(readAccessor(presentation, "displayName")).isEqualTo("正文生成");

        Object exposure = readAccessor(descriptor, "exposure");
        assertThat(readAccessor(exposure, "exposedToLlm")).isEqualTo(true);
        String schema = String.valueOf(readAccessor(exposure, "parametersJsonSchema"));
        assertThat(schema)
                .contains("\"operation\"")
                .contains("generate")
                .contains("rewrite")
                .contains("revise")
                .contains("\"prompt\"")
                .contains("\"sourceText\"")
                .contains("\"instruction\"")
                .contains("\"preservedConstraints\"")
                .contains("\"sourceSummary\"");

        Object governancePolicy = readAccessor(descriptor, "governancePolicy");
        assertThat(readAccessor(governancePolicy, "riskLevel")).isEqualTo(1);
        Object defaultDecision = readAccessor(governancePolicy, "defaultDecision");
        assertThat(readAccessor(defaultDecision, "approvalRequired")).isEqualTo(false);
        Map<?, ?> operationPolicies = castMap(readAccessor(governancePolicy, "operationPolicies"));
        assertThat(operationPolicies.containsKey("generate")).isTrue();
        assertThat(operationPolicies.containsKey("rewrite")).isTrue();
        assertThat(operationPolicies.containsKey("revise")).isTrue();
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_DEFINITION_SCHEMA_SHOULD_MATCH_OPERATION_SPECIFIC_REQUIRED_FIELDS() throws Exception {
        Object definition = instantiateDraftGenerationToolDefinition();
        Object descriptor = definition.getClass().getMethod("descriptor").invoke(definition);
        Object exposure = readAccessor(descriptor, "exposure");
        String schema = String.valueOf(readAccessor(exposure, "parametersJsonSchema"));

        assertThat(schema)
                .contains("\"oneOf\"")
                .contains("\"const\": \"generate\"")
                .contains("\"required\": [\"operation\", \"prompt\"]")
                .contains("\"const\": \"rewrite\"")
                .contains("\"required\": [\"operation\", \"sourceText\", \"instruction\"]")
                .contains("\"const\": \"revise\"")
                .contains("\"additionalProperties\": false");
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_HANDLER_EXECUTE_SHOULD_RETURN_STRUCTURED_RESULT_FOR_GENERATE_OPERATION() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateDraftGenerationToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-1")).thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", "generated draft", List.of(), "{}"));

        ToolCallResult result = execute(handler, request("call-generate", """
                {
                  "operation": "generate",
                  "prompt": "根据第三章大纲生成初稿",
                  "preservedConstraints": ["保留第一人称", "保留女主冷静口吻"],
                  "sourceSummary": "第三章冲突提纲"
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getStr("draftText")).isEqualTo("generated draft");
        assertThat(output.getStr("operation")).isEqualTo("generate");
        assertThat(output.getStr("sourceSummary")).isEqualTo("第三章冲突提纲");
        JSONArray preservedConstraints = output.getJSONArray("preservedConstraints");
        assertThat(preservedConstraints).isNotNull();
        assertThat(preservedConstraints.toList(String.class))
                .containsExactly("保留第一人称", "保留女主冷静口吻");

        ArgumentCaptor<AgentLlmTurnRequest> taskCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway).generateTurn(taskCaptor.capture(), eq(executionConfig));
        assertThat(taskCaptor.getValue().messages().get(0).content())
                .contains("根据第三章大纲生成初稿")
                .contains("保留第一人称")
                .contains("第三章冲突提纲");
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_HANDLER_EXECUTE_SHOULD_USE_RUN_IDENTITY_WITHOUT_LOADING_GENERATION_TASK() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateDraftGenerationToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-1")).thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", "run scoped draft", List.of(), "{}"));

        ToolCallResult result = execute(handler, request("call-run-scoped-generate", """
                {
                  "operation": "generate",
                  "prompt": "Generate from run context"
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        verifyNoInteractions(agentRepository);
        ArgumentCaptor<AgentLlmTurnRequest> turnRequestCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway).generateTurn(turnRequestCaptor.capture(), eq(executionConfig));
        assertThat(turnRequestCaptor.getValue().messages()).singleElement().satisfies(message -> {
            assertThat(message.content()).contains("Generate from run context");
        });
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_HANDLER_EXECUTE_SHOULD_RETURN_STRUCTURED_RESULT_FOR_REWRITE_OPERATION() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateDraftGenerationToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-1")).thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", "rewritten draft", List.of(), "{}"));

        ToolCallResult result = execute(handler, request("call-rewrite", """
                {
                  "operation": "rewrite",
                  "sourceText": "原文第一版",
                  "instruction": "改写成更冷峻紧张的叙述",
                  "preservedConstraints": ["剧情走向不变"],
                  "sourceSummary": "保留原段落剧情节点"
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getStr("draftText")).isEqualTo("rewritten draft");
        assertThat(output.getStr("operation")).isEqualTo("rewrite");
        assertThat(output.getStr("sourceSummary")).isEqualTo("保留原段落剧情节点");
        assertThat(output.getJSONArray("preservedConstraints").toList(String.class))
                .containsExactly("剧情走向不变");

        ArgumentCaptor<AgentLlmTurnRequest> taskCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway).generateTurn(taskCaptor.capture(), eq(executionConfig));
        assertThat(taskCaptor.getValue().messages().get(0).content())
                .contains("原文第一版")
                .contains("改写成更冷峻紧张的叙述")
                .contains("剧情走向不变");
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_HANDLER_EXECUTE_SHOULD_RETURN_STRUCTURED_RESULT_FOR_REVISE_OPERATION() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateDraftGenerationToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-1")).thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", "revised draft", List.of(), "{}"));

        ToolCallResult result = execute(handler, request("call-revise", """
                {
                  "operation": "revise",
                  "sourceText": "初稿正文",
                  "instruction": "按照审查建议修复节奏与角色动机",
                  "preservedConstraints": ["结局不变", "保留第二人称称呼"],
                  "sourceSummary": "来源于质量审查建议"
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        JSONObject output = AgentJsonCodec.parseObj(result.toolOutput());
        assertThat(output.getStr("draftText")).isEqualTo("revised draft");
        assertThat(output.getStr("operation")).isEqualTo("revise");
        assertThat(output.getStr("sourceSummary")).isEqualTo("来源于质量审查建议");
        assertThat(output.getJSONArray("preservedConstraints").toList(String.class))
                .containsExactly("结局不变", "保留第二人称称呼");

        ArgumentCaptor<AgentLlmTurnRequest> taskCaptor = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(agentLlmGateway).generateTurn(taskCaptor.capture(), eq(executionConfig));
        assertThat(taskCaptor.getValue().messages().get(0).content())
                .contains("初稿正文")
                .contains("按照审查建议修复节奏与角色动机")
                .contains("结局不变");
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_HANDLER_EXECUTE_SHOULD_MAP_PROVIDER_EXCEPTION_TO_STABLE_FAILED_RESULT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateDraftGenerationToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-1")).thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenThrow(new RuntimeException("provider timeout"));

        ToolCallResult result = execute(handler, request("call-failed", """
                {
                  "operation": "generate",
                  "prompt": "生成一版失败样例",
                  "preservedConstraints": ["保留人设"],
                  "sourceSummary": "失败路径"
                }
                """));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("DRAFT_GENERATION_FAILED");
        assertThat(result.errorMessage()).isEqualTo("provider timeout");
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_HANDLER_EXECUTE_SHOULD_REJECT_BLANK_DRAFT_TEXT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateDraftGenerationToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-1")).thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", "   ", List.of(), "{}"));

        ToolCallResult result = execute(handler, request("call-blank-draft", """
                {
                  "operation": "generate",
                  "prompt": "生成一版空正文样例",
                  "preservedConstraints": ["保留人设"],
                  "sourceSummary": "空正文防呆"
                }
                """));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("DRAFT_GENERATION_FAILED");
        assertThat(result.errorMessage()).contains("draft text");
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_HANDLER_EXECUTE_SHOULD_REJECT_PLACEHOLDER_OK_DRAFT_TEXT() throws Exception {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentModelRoutingService agentModelRoutingService = mock(AgentModelRoutingService.class);
        AgentLlmGateway agentLlmGateway = mock(AgentLlmGateway.class);
        Object handler = instantiateDraftGenerationToolHandler(agentRepository, agentModelRoutingService, agentLlmGateway);
        AgentLlmExecutionConfig executionConfig = executionConfig();

        when(agentModelRoutingService.resolveExecutionConfig(1001L, null, "trace-1")).thenReturn(executionConfig);
        when(agentLlmGateway.generateTurn(any(AgentLlmTurnRequest.class), eq(executionConfig)))
                .thenReturn(new AgentLlmTurnResponse("stop", "ok", List.of(), "{}"));

        ToolCallResult result = execute(handler, request("call-placeholder-draft", """
                {
                  "operation": "generate",
                  "prompt": "生成一版占位正文样例",
                  "preservedConstraints": ["保留人设"],
                  "sourceSummary": "占位正文防呆"
                }
                """));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("DRAFT_GENERATION_FAILED");
        assertThat(result.errorMessage()).contains("draft text");
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_CALL_APPLICATION_SERVICE_SHOULD_PUBLISH_GENERATE_TOOL_CALL_SEMANTICS_ON_SUCCESS() throws Exception {
        AgentToolDefinitionSource toolDefinitionSource = mock(AgentToolDefinitionSource.class);
        DefaultApprovalPolicyEngine approvalPolicyEngine = mock(DefaultApprovalPolicyEngine.class);
        ToolApprovalViewFactory toolApprovalViewFactory = mock(ToolApprovalViewFactory.class);
        ApprovalApplicationService approvalApplicationService = mock(ApprovalApplicationService.class);
        AgentRunPendingApprovalRepository pendingApprovalRepository = mock(AgentRunPendingApprovalRepository.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
        AgentToolHandler handler = mock(AgentToolHandler.class);
        ToolCallExecutionService executionService = new ToolCallExecutionService(List.of(handler));
        ToolCallApplicationService applicationService = new ToolCallApplicationService(
                toolDefinitionSource,
                approvalPolicyEngine,
                toolApprovalViewFactory,
                approvalApplicationService,
                pendingApprovalRepository,
                executionService
        );
        ToolCallRequest request = request("call-generate", """
                {
                  "operation": "generate",
                  "prompt": "生成第一版正文",
                  "preservedConstraints": ["保留第一人称"],
                  "sourceSummary": "第三章提纲"
                }
                """);
        AgentToolDescriptor descriptor = loadDraftGenerationDescriptor();
        String outputJson = AgentJsonCodec.toJson(Map.of(
                "draftText", "第一版正文",
                "operation", "generate",
                "preservedConstraints", List.of("保留第一人称"),
                "sourceSummary", "第三章提纲"
        ));

        when(handler.toolCode()).thenReturn("draft_generation");
        when(toolDefinitionSource.getRequired("draft_generation")).thenReturn(descriptor);
        when(approvalPolicyEngine.evaluate(descriptor, request)).thenReturn(new ApprovalPolicyDecision(false, ""));
        when(handler.execute(request)).thenReturn(ToolCallResult.success(outputJson));

        ToolCallResult result = applicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_CALL_APPLICATION_SERVICE_SHOULD_PUBLISH_REWRITE_TOOL_CALL_SEMANTICS_ON_FAILURE() throws Exception {
        AgentToolDefinitionSource toolDefinitionSource = mock(AgentToolDefinitionSource.class);
        DefaultApprovalPolicyEngine approvalPolicyEngine = mock(DefaultApprovalPolicyEngine.class);
        ToolApprovalViewFactory toolApprovalViewFactory = mock(ToolApprovalViewFactory.class);
        ApprovalApplicationService approvalApplicationService = mock(ApprovalApplicationService.class);
        AgentRunPendingApprovalRepository pendingApprovalRepository = mock(AgentRunPendingApprovalRepository.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
        AgentToolHandler handler = mock(AgentToolHandler.class);
        ToolCallExecutionService executionService = new ToolCallExecutionService(List.of(handler));
        ToolCallApplicationService applicationService = new ToolCallApplicationService(
                toolDefinitionSource,
                approvalPolicyEngine,
                toolApprovalViewFactory,
                approvalApplicationService,
                pendingApprovalRepository,
                executionService
        );
        ToolCallRequest request = request("call-rewrite", """
                {
                  "operation": "rewrite",
                  "sourceText": "原文第一版",
                  "instruction": "改写得更有压迫感",
                  "preservedConstraints": ["剧情走向不变"],
                  "sourceSummary": "保留剧情节点"
                }
                """);
        AgentToolDescriptor descriptor = loadDraftGenerationDescriptor();

        when(handler.toolCode()).thenReturn("draft_generation");
        when(toolDefinitionSource.getRequired("draft_generation")).thenReturn(descriptor);
        when(approvalPolicyEngine.evaluate(descriptor, request)).thenReturn(new ApprovalPolicyDecision(false, ""));
        when(handler.execute(request)).thenReturn(new ToolCallResult(
                "FAILED",
                null,
                null,
                "DRAFT_GENERATION_FAILED",
                "provider timeout"
        ));

        ToolCallResult result = applicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("FAILED");
    }

    @Test
    void UT_APP_AGENT_DRAFT_GENERATION_TOOL_CALL_APPLICATION_SERVICE_SHOULD_PUBLISH_REVISE_TOOL_CALL_SEMANTICS_WHEN_WAITING_APPROVAL() throws Exception {
        AgentToolDefinitionSource toolDefinitionSource = mock(AgentToolDefinitionSource.class);
        DefaultApprovalPolicyEngine approvalPolicyEngine = mock(DefaultApprovalPolicyEngine.class);
        ToolApprovalViewFactory toolApprovalViewFactory = mock(ToolApprovalViewFactory.class);
        ApprovalApplicationService approvalApplicationService = mock(ApprovalApplicationService.class);
        AgentRunPendingApprovalRepository pendingApprovalRepository = mock(AgentRunPendingApprovalRepository.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
        AgentToolHandler handler = mock(AgentToolHandler.class);
        ToolCallExecutionService executionService = new ToolCallExecutionService(List.of(handler));
        ToolCallApplicationService applicationService = new ToolCallApplicationService(
                toolDefinitionSource,
                approvalPolicyEngine,
                toolApprovalViewFactory,
                approvalApplicationService,
                pendingApprovalRepository,
                executionService
        );
        ToolCallRequest request = request("call-revise", """
                {
                  "operation": "revise",
                  "sourceText": "初稿正文",
                  "instruction": "按质量建议修复节奏",
                  "preservedConstraints": ["结局不变"],
                  "sourceSummary": "质量审查建议"
                }
                """);
        AgentToolDescriptor descriptor = loadDraftGenerationDescriptor();
        ToolApprovalView approvalView = new ToolApprovalView(
                "draft_generation",
                "正文生成",
                1,
                "DRAFT_REVIEW",
                "revise"
        );
        ApprovalRequest approvalRequest = new ApprovalRequest();
        approvalRequest.setId(1L);
        approvalRequest.setApprovalRequestId(55L);

        when(toolDefinitionSource.getRequired("draft_generation")).thenReturn(descriptor);
        when(approvalPolicyEngine.evaluate(descriptor, request)).thenReturn(new ApprovalPolicyDecision(true, "DRAFT_REVIEW"));
        when(toolApprovalViewFactory.create(descriptor, new ApprovalPolicyDecision(true, "DRAFT_REVIEW"))).thenReturn(approvalView);
        when(approvalApplicationService.create(any(), eq("trace-1"))).thenReturn(approvalRequest);

        ToolCallResult result = applicationService.executeToolCall(request);

        assertThat(result.status()).isEqualTo("WAITING_APPROVAL");
        assertThat(result.approvalId()).isEqualTo(55L);
    }

    private ToolCallRequest request(String toolCallId, String toolArgsJson) {
        return new ToolCallRequest(
                9001L,
                8001L,
                7001L,
                6001L,
                "draft_generation",
                toolArgsJson,
                1001L,
                "trace-1",
                "{}",
                toolCallId + "-idem",
                0,
                toolCallId,
                "[{\"id\":\"" + toolCallId + "\"}]",
                "[{\"role\":\"user\",\"content\":\"请处理正文\"}]",
                "RESUME_LOOP",
                null
        );
    }


    private AgentLlmExecutionConfig executionConfig() {
        return AgentLlmExecutionConfig.builder()
                .modelConfigId(7001L)
                .providerCode("openai-compatible")
                .baseUrl("https://example.test/v1")
                .apiKey("secret")
                .modelName("gpt-test")
                .keySource("MODEL_CONFIG")
                .build();
    }

    private AgentToolDescriptor loadDraftGenerationDescriptor() throws Exception {
        Object definition = instantiateDraftGenerationToolDefinition();
        return (AgentToolDescriptor) definition.getClass().getMethod("descriptor").invoke(definition);
    }

    private Object instantiateDraftGenerationToolDefinition() throws Exception {
        Class<?> type = Class.forName("com.penmate.backend.application.agent.tool.definition.DraftGenerationToolDefinition");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private Object instantiateDraftGenerationToolHandler(AgentRepository agentRepository,
                                                         AgentModelRoutingService agentModelRoutingService,
                                                         AgentLlmGateway agentLlmGateway) throws Exception {
        Class<?> type = Class.forName("com.penmate.backend.application.agent.tool.handler.DraftGenerationToolHandler");
        Constructor<?> constructor = type.getDeclaredConstructor(AgentModelRoutingService.class, AgentLlmGateway.class);
        constructor.setAccessible(true);
        return constructor.newInstance(agentModelRoutingService, agentLlmGateway);
    }

    private ToolCallResult execute(Object handler, ToolCallRequest request) throws Exception {
        Method method = handler.getClass().getMethod("execute", ToolCallRequest.class);
        try {
            return (ToolCallResult) method.invoke(handler, request);
        } catch (InvocationTargetException ex) {
            Throwable target = ex.getTargetException();
            if (target instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(target);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> castMap(Object value) {
        return (Map<?, ?>) value;
    }

    private Object readAccessor(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }
}
