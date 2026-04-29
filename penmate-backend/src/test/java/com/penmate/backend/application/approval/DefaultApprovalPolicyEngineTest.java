package com.penmate.backend.application.approval;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultApprovalPolicyEngineTest {

    @Test
    void UT_APP_APPROVAL_POLICY_ENGINE_REQUIRES_APPROVAL_FOR_BOOK_CRUD_DELETE() {
        Object engine = instantiate("com.penmate.backend.application.approval.DefaultApprovalPolicyEngine");
        Object metadata = newToolMetadata("book_crud", "书籍 CRUD", false, "BOOK_CRUD", 2);
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

        Object decision = invoke(engine, "evaluate", metadata, request);

        assertThat(invokeAccessor(decision, "approvalRequired")).isEqualTo(true);
        assertThat(invokeAccessor(decision, "approvalType")).isEqualTo("BOOK_DELETE");
    }

    @Test
    void UT_APP_APPROVAL_POLICY_ENGINE_BYPASSES_APPROVAL_FOR_CONTEXT_ENHANCER() {
        Object engine = instantiate("com.penmate.backend.application.approval.DefaultApprovalPolicyEngine");
        Object metadata = newToolMetadata("context_enhancer", "上下文增强", false, "", 1);
        Object request = newToolInvocationRequest(
                10001L,
                8002L,
                7001L,
                "context_enhancer",
                "{\"prompt\":\"补充冲突\"}",
                1001L,
                "trace-context-1",
                "{}",
                "context-enhancer-8002"
        );

        Object decision = invoke(engine, "evaluate", metadata, request);

        assertThat(invokeAccessor(decision, "approvalRequired")).isEqualTo(false);
        assertThat(invokeAccessor(decision, "approvalType")).isEqualTo("");
    }

    private Object instantiate(String className) {
        try {
            Class<?> type = Class.forName(className);
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception ex) {
            throw new AssertionError("expected class to be constructible: " + className, ex);
        }
    }

    private Object newToolMetadata(String toolCode,
                                   String displayName,
                                   boolean approvalRequired,
                                   String approvalType,
                                   Integer riskLevel) {
        try {
            Class<?> type = Class.forName("com.penmate.backend.application.agent.ToolMetadata");
            Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class, boolean.class, String.class, Integer.class);
            constructor.setAccessible(true);
            return constructor.newInstance(toolCode, displayName, approvalRequired, approvalType, riskLevel);
        } catch (Exception ex) {
            throw new AssertionError("expected tool metadata type to be constructible", ex);
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
            Class<?> type = Class.forName("com.penmate.backend.application.agent.ToolInvocationRequest");
            Constructor<?> constructor = type.getDeclaredConstructor(
                    Long.class,
                    Long.class,
                    Long.class,
                    String.class,
                    String.class,
                    Long.class,
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
                    idempotencyKey
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
