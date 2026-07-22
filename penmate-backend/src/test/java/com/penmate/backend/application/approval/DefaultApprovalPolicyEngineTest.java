package com.penmate.backend.application.approval;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultApprovalPolicyEngineTest {

    @Test
    void UT_APP_APPROVAL_POLICY_ENGINE_MATCHES_OPERATION_POLICY_FOR_BOOK_CRUD_DELETE() {
        Object engine = instantiate("com.penmate.backend.application.approval.DefaultApprovalPolicyEngine");
        Object descriptor = newToolDescriptor(
                "book_crud",
                "书籍 CRUD",
                "书籍 CRUD；必须提供 operation",
                defaultDecision(false, "", null, null, null),
                2,
                Map.of("delete", operationPolicy("delete", decision(true, "BOOK_DELETE", 2, "delete", "书籍 CRUD")))
        );
        Object request = newToolInvocationRequest(
                10001L,
                8001L,
                7001L,
                "book_crud",
                "{\"operation\":\"delete\",\"bookId\":9001}",
                1001L,
                "trace-delete-1",
                "{}",
                "book-crud-delete-9001"
        );

        Object decision = invoke(engine, "evaluate", descriptor, request);

        assertThat(invokeAccessor(decision, "approvalRequired")).isEqualTo(true);
        assertThat(invokeAccessor(decision, "approvalType")).isEqualTo("BOOK_DELETE");
        assertThat(invokeAccessor(decision, "riskLevel")).isEqualTo(2);
        assertThat(invokeAccessor(decision, "operationCode")).isEqualTo("delete");
        assertThat(invokeAccessor(decision, "displayName")).isEqualTo("书籍 CRUD");
    }

    @Test
    void UT_APP_APPROVAL_POLICY_ENGINE_BYPASSES_APPROVAL_FOR_BOOK_CRUD_LIST_WITH_DEFAULT_DECISION() {
        Object engine = instantiate("com.penmate.backend.application.approval.DefaultApprovalPolicyEngine");
        Object descriptor = newToolDescriptor(
                "book_crud",
                "书籍 CRUD",
                "书籍 CRUD；必须提供 operation",
                defaultDecision(false, "", null, null, null),
                2,
                Map.of("delete", operationPolicy("delete", decision(true, "BOOK_DELETE", 2, "delete", "书籍 CRUD")))
        );
        Object request = newToolInvocationRequest(
                10001L,
                8002L,
                7001L,
                "book_crud",
                "{\"operation\":\"list\",\"page\":1}",
                1001L,
                "trace-list-1",
                "{}",
                "book-crud-list-1"
        );

        Object decision = invoke(engine, "evaluate", descriptor, request);

        assertThat(invokeAccessor(decision, "approvalRequired")).isEqualTo(false);
        assertThat(invokeAccessor(decision, "approvalType")).isEqualTo("");
        assertThat(invokeAccessor(decision, "riskLevel")).isEqualTo(2);
        assertThat(invokeAccessor(decision, "operationCode")).isEqualTo("list");
        assertThat(invokeAccessor(decision, "displayName")).isEqualTo("书籍 CRUD");
    }

    @Test
    void UT_APP_APPROVAL_POLICY_ENGINE_IGNORES_NESTED_OPERATION_AND_FALLS_BACK_TO_DEFAULT_DECISION() {
        Object engine = instantiate("com.penmate.backend.application.approval.DefaultApprovalPolicyEngine");
        Object descriptor = newToolDescriptor(
                "book_crud",
                "书籍 CRUD",
                "书籍 CRUD；必须提供 operation",
                defaultDecision(false, "", null, null, null),
                2,
                Map.of("delete", operationPolicy("delete", decision(true, "BOOK_DELETE", 2, "delete", "书籍 CRUD")))
        );
        Object request = newToolInvocationRequest(
                10001L,
                8004L,
                7001L,
                "book_crud",
                "{\"payload\":{\"operation\":\"delete\"},\"bookId\":9001}",
                1001L,
                "trace-nested-delete-1",
                "{}",
                "book-crud-nested-delete-9001"
        );

        Object decision = invoke(engine, "evaluate", descriptor, request);

        assertThat(invokeAccessor(decision, "approvalRequired")).isEqualTo(false);
        assertThat(invokeAccessor(decision, "approvalType")).isEqualTo("");
        assertThat(invokeAccessor(decision, "riskLevel")).isEqualTo(2);
        assertThat(invokeAccessor(decision, "operationCode")).isNull();
        assertThat(invokeAccessor(decision, "displayName")).isEqualTo("书籍 CRUD");
    }

    @Test
    void UT_APP_APPROVAL_POLICY_ENGINE_MATCHES_GOVERNANCE_DEFAULT_APPROVAL() {
        Object engine = instantiate("com.penmate.backend.application.approval.DefaultApprovalPolicyEngine");
        Object descriptor = newToolDescriptor(
                "dangerous_export",
                "危险导出",
                "危险导出工具",
                defaultDecision(true, "SENSITIVE_EXPORT", 4, null, "危险导出"),
                4,
                Map.of()
        );
        Object request = newToolInvocationRequest(
                10001L,
                8003L,
                7001L,
                "dangerous_export",
                "{\"scope\":\"all\"}",
                1001L,
                "trace-export-1",
                "{}",
                "dangerous-export-1"
        );

        Object decision = invoke(engine, "evaluate", descriptor, request);

        assertThat(invokeAccessor(decision, "approvalRequired")).isEqualTo(true);
        assertThat(invokeAccessor(decision, "approvalType")).isEqualTo("SENSITIVE_EXPORT");
        assertThat(invokeAccessor(decision, "riskLevel")).isEqualTo(4);
        assertThat(invokeAccessor(decision, "operationCode")).isNull();
        assertThat(invokeAccessor(decision, "displayName")).isEqualTo("危险导出");
    }

    private Object instantiate(String className) {
        try {
            Class<?> type = Class.forName(className);
            Constructor<?> constructor = type.getDeclaredConstructor(JsonCodec.class);
            constructor.setAccessible(true);
            return constructor.newInstance(new JacksonJsonCodec(new ObjectMapper()));
        } catch (Exception ex) {
            throw new AssertionError("expected class to be constructible: " + className, ex);
        }
    }

    private Object newToolDescriptor(String toolCode,
                                     String displayName,
                                     String llmDescription,
                                     Object defaultDecision,
                                     Integer riskLevel,
                                     Map<String, Object> operationPolicies) {
        try {
            Class<?> descriptorType = Class.forName("com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor");
            Class<?> presentationType = Class.forName("com.penmate.backend.application.agent.tool.definition.ToolPresentation");
            Class<?> exposureType = Class.forName("com.penmate.backend.application.agent.tool.definition.ToolExposure");
            Class<?> governanceType = Class.forName("com.penmate.backend.application.agent.tool.definition.ToolGovernancePolicy");

            Object presentation = presentationType
                    .getDeclaredConstructor(String.class)
                    .newInstance(displayName);
            Object exposure = exposureType
                    .getDeclaredConstructor(boolean.class, String.class, String.class)
                    .newInstance(true, llmDescription, "{\"type\":\"object\"}");
            Object governance = governanceType
                    .getDeclaredConstructor(Class.forName("com.penmate.backend.application.approval.ApprovalPolicyDecision"), Integer.class, Map.class)
                    .newInstance(defaultDecision, riskLevel, operationPolicies);

            return descriptorType
                    .getDeclaredConstructor(String.class, presentationType, exposureType, governanceType)
                    .newInstance(toolCode, presentation, exposure, governance);
        } catch (Exception ex) {
            throw new AssertionError("expected tool descriptor type to be constructible", ex);
        }
    }

    private Object operationPolicy(String operationCode, Object decision) {
        try {
            Class<?> type = Class.forName("com.penmate.backend.application.agent.tool.definition.ToolOperationPolicy");
            Constructor<?> constructor = type.getDeclaredConstructor(
                    String.class,
                    Class.forName("com.penmate.backend.application.approval.ApprovalPolicyDecision")
            );
            constructor.setAccessible(true);
            return constructor.newInstance(operationCode, decision);
        } catch (Exception ex) {
            throw new AssertionError("expected operation policy type to be constructible", ex);
        }
    }

    private Object defaultDecision(boolean approvalRequired,
                                   String approvalType,
                                   Integer riskLevel,
                                   String operationCode,
                                   String displayName) {
        return decision(approvalRequired, approvalType, riskLevel, operationCode, displayName);
    }

    private Object decision(boolean approvalRequired,
                            String approvalType,
                            Integer riskLevel,
                            String operationCode,
                            String displayName) {
        try {
            Class<?> type = Class.forName("com.penmate.backend.application.approval.ApprovalPolicyDecision");
            Constructor<?> constructor = type.getDeclaredConstructor(boolean.class, String.class, Integer.class, String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(approvalRequired, approvalType, riskLevel, operationCode, displayName);
        } catch (Exception ex) {
            throw new AssertionError("expected approval policy decision type to be constructible", ex);
        }
    }

    private Object newToolInvocationRequest(Long projectId,
                                            Long taskId,
                                            Long conversationId,
                                            String toolCode,
                                            String toolArgsJson,
                                            Long operatorId,
                                            String traceId,
                                            String contextJson,
                                            String idempotencyKey) {
        try {
            Class<?> type = Class.forName("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
            Constructor<?> constructor = type.getDeclaredConstructor(
                    Long.class,
                    Long.class,
                    Long.class,
                    String.class,
                    String.class,
                    Long.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    Integer.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    projectId,
                    taskId,
                    conversationId,
                    toolCode,
                    toolArgsJson,
                    operatorId,
                    traceId,
                    contextJson,
                    idempotencyKey,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        } catch (Exception ex) {
            throw new AssertionError("expected tool invocation request type to be constructible", ex);
        }
    }

    private Object invoke(Object target, String methodName, Object arg1, Object arg2) {
        try {
            Method method = target.getClass().getMethod(methodName, arg1.getClass(), arg2.getClass());
            return method.invoke(target, arg1, arg2);
        } catch (Exception ex) {
            throw new AssertionError("expected method invocation to succeed: " + methodName, ex);
        }
    }

    private Object invokeAccessor(Object target, String accessorName) {
        try {
            Method method = target.getClass().getMethod(accessorName);
            return method.invoke(target);
        } catch (Exception ex) {
            throw new AssertionError("expected accessor invocation to succeed: " + accessorName, ex);
        }
    }
}
