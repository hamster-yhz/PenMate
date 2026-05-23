package com.penmate.backend.application.agent.tool.handler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TodoCrudToolHandlerTest {

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_DEFINITION_SHOULD_EXPOSE_PROVIDER_COMPATIBLE_TOP_LEVEL_SCHEMA_AND_NON_APPROVAL_GOVERNANCE() throws Exception {
        Object definition = instantiateNoArgsClass(
                "com.penmate.backend.application.agent.tool.definition.TodoCrudToolDefinition"
        );
        Object descriptor = definition.getClass().getMethod("descriptor").invoke(definition);

        assertThat(readAccessor(descriptor, "toolCode")).isEqualTo("todo_crud");

        Object presentation = readAccessor(descriptor, "presentation");
        assertThat(readAccessor(presentation, "displayName")).isEqualTo("待办 CRUD");

        Object exposure = readAccessor(descriptor, "exposure");
        String schema = String.valueOf(readAccessor(exposure, "parametersJsonSchema"));
        assertThat(schema)
                .contains("\"type\": \"object\"")
                .contains("\"operation\"")
                .contains("\"list\"")
                .contains("\"create\"")
                .contains("\"update\"")
                .contains("\"complete\"")
                .contains("\"delete\"")
                .contains("\"sessionId\"")
                .contains("\"todoId\"")
                .contains("\"title\"")
                .contains("\"todoStatus\"")
                .contains("\"required\": [\"operation\", \"sessionId\"]")
                .contains("\"additionalProperties\": false")
                .doesNotContain("\"oneOf\"")
                .doesNotContain("\"anyOf\"")
                .doesNotContain("\"allOf\"")
                .doesNotContain("\"not\"");

        Object governancePolicy = readAccessor(descriptor, "governancePolicy");
        Object defaultDecision = readAccessor(governancePolicy, "defaultDecision");
        assertThat(readAccessor(defaultDecision, "approvalRequired")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> operationPolicies = (Map<String, Object>) readAccessor(governancePolicy, "operationPolicies");
        assertThat(operationPolicies).isEmpty();
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_SHOULD_EXPOSE_STABLE_TOOL_CODE() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Object handler = instantiateTodoCrudHandler(org.mockito.Mockito.mock(serviceClass));

        assertThat(handler.getClass().getMethod("toolCode").invoke(handler)).isEqualTo("todo_crud");
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_CREATE_WITHOUT_REQUIRED_FIELDS() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Class<?> requestClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
        Object handler = instantiateTodoCrudHandler(org.mockito.Mockito.mock(serviceClass));
        Object request = newShortToolRequest(requestClass,
                "{\"operation\":\"create\",\"sessionId\":\"9\"}",
                "trace-todo-validate-create");

        assertThatThrownBy(() -> handler.getClass().getMethod("validate", requestClass).invoke(handler, request))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("title is required");
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_LIST_WITH_WRITE_FIELDS() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Class<?> requestClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
        Object handler = instantiateTodoCrudHandler(org.mockito.Mockito.mock(serviceClass));
        Object request = newShortToolRequest(requestClass,
                "{\"operation\":\"list\",\"sessionId\":\"9\",\"title\":\"不应出现\"}",
                "trace-todo-validate-list");

        assertThatThrownBy(() -> handler.getClass().getMethod("validate", requestClass).invoke(handler, request))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Unexpected field for operation list: title");
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_SESSION_ID_LESS_THAN_ONE() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Class<?> requestClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
        Object handler = instantiateTodoCrudHandler(org.mockito.Mockito.mock(serviceClass));
        Object request = newShortToolRequest(requestClass,
                "{\"operation\":\"list\",\"sessionId\":\"0\"}",
                "trace-todo-validate-session-id");

        assertThatThrownBy(() -> handler.getClass().getMethod("validate", requestClass).invoke(handler, request))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("sessionId must be greater than or equal to 1");
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_TODO_ID_LESS_THAN_ONE() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Class<?> requestClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
        Object handler = instantiateTodoCrudHandler(org.mockito.Mockito.mock(serviceClass));
        Object request = newShortToolRequest(requestClass,
                "{\"operation\":\"complete\",\"sessionId\":\"9\",\"todoId\":\"-1\"}",
                "trace-todo-validate-todo-id");

        assertThatThrownBy(() -> handler.getClass().getMethod("validate", requestClass).invoke(handler, request))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("todoId must be greater than or equal to 1");
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_TASK_ID_LESS_THAN_ONE_WHEN_PRESENT() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Class<?> requestClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
        Object handler = instantiateTodoCrudHandler(org.mockito.Mockito.mock(serviceClass));
        Object request = newShortToolRequest(requestClass,
                "{\"operation\":\"create\",\"sessionId\":\"9\",\"taskId\":\"0\",\"title\":\"新增待办\",\"sourceType\":\"PLANNING\",\"todoStatus\":\"TODO\"}",
                "trace-todo-validate-task-id");

        assertThatThrownBy(() -> handler.getClass().getMethod("validate", requestClass).invoke(handler, request))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("taskId must be greater than or equal to 1");
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_SHOULD_UPDATE_TODO_AND_RETURN_STRUCTURED_OUTPUT() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Class<?> sessionTodoClass = loadClass("com.penmate.backend.domain.todo.model.SessionTodo");
        Class<?> requestClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
        Class<?> resultClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallResult");
        Object serviceMock = org.mockito.Mockito.mock(serviceClass);
        Object updatedTodo = instantiateNoArgsClass("com.penmate.backend.domain.todo.model.SessionTodo");
        writeField(updatedTodo, "todoId", 20031L);
        writeField(updatedTodo, "projectId", 1L);
        writeField(updatedTodo, "sessionId", 9L);
        writeField(updatedTodo, "taskId", 88L);
        writeField(updatedTodo, "title", "修订第三章开场");
        writeField(updatedTodo, "description", "补齐密令来源桥段");
        writeField(updatedTodo, "sourceType", "PLANNING");
        writeField(updatedTodo, "todoStatus", "BLOCKED");
        writeField(updatedTodo, "updatedAt", LocalDateTime.of(2026, 5, 15, 22, 0, 0));

        Method updateMethod = serviceClass.getMethod(
                "updateTodo",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                sessionTodoClass,
                Long.class,
                String.class
        );
        Object stubbingTarget = org.mockito.Mockito.doReturn(updatedTodo).when(serviceMock);
        updateMethod.invoke(
                stubbingTarget,
                1L,
                9L,
                20031L,
                88L,
                buildTodoCandidate(),
                1001L,
                "trace-todo-update"
        );

        Object handler = instantiateTodoCrudHandler(serviceMock);
        Object request = requestClass.getDeclaredConstructor(
                        Long.class,
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        Long.class,
                        String.class,
                        String.class,
                        String.class
                )
                .newInstance(
                        1L,
                        77L,
                        9L,
                        "todo_crud",
                        "{\"operation\":\"update\",\"sessionId\":\"9\",\"todoId\":\"20031\",\"taskId\":\"88\",\"title\":\"修订第三章开场\",\"description\":\"补齐密令来源桥段\",\"sourceType\":\"PLANNING\",\"todoStatus\":\"BLOCKED\"}",
                        1001L,
                        "trace-todo-update",
                        "{}",
                        "todo-call-1"
                );

        Object result = handler.getClass().getMethod("execute", requestClass).invoke(handler, request);

        assertThat(resultClass.getMethod("status").invoke(result)).isEqualTo("SUCCESS");
        assertThat(String.valueOf(resultClass.getMethod("toolOutput").invoke(result)))
                .contains("\"todoId\":\"20031\"")
                .contains("\"sessionId\":\"9\"")
                .contains("\"taskId\":\"88\"")
                .contains("\"title\":\"修订第三章开场\"")
                .contains("\"sourceType\":\"PLANNING\"")
                .contains("\"todoStatus\":\"BLOCKED\"");
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_SHOULD_CREATE_TODO_AND_RETURN_STRUCTURED_OUTPUT() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Class<?> sessionTodoClass = loadClass("com.penmate.backend.domain.todo.model.SessionTodo");
        Class<?> requestClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
        Class<?> resultClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallResult");
        Object serviceMock = org.mockito.Mockito.mock(serviceClass);
        Object createdTodo = instantiateNoArgsClass("com.penmate.backend.domain.todo.model.SessionTodo");
        writeField(createdTodo, "todoId", 20041L);
        writeField(createdTodo, "projectId", 1L);
        writeField(createdTodo, "sessionId", 9L);
        writeField(createdTodo, "taskId", 77L);
        writeField(createdTodo, "title", "新增侍从转述桥段");
        writeField(createdTodo, "description", "补齐密令传递链路");
        writeField(createdTodo, "sourceType", "PLANNING");
        writeField(createdTodo, "todoStatus", "TODO");

        Method createMethod = serviceClass.getMethod(
                "createTodo",
                Long.class,
                Long.class,
                Long.class,
                sessionTodoClass,
                Long.class,
                String.class
        );
        Object stubbingTarget = org.mockito.Mockito.doReturn(createdTodo).when(serviceMock);
        createMethod.invoke(stubbingTarget, 1L, 9L, 77L, buildTodoCandidateWith("新增侍从转述桥段", "补齐密令传递链路", "PLANNING", "TODO"), 1001L, "trace-todo-create");

        Object handler = instantiateTodoCrudHandler(serviceMock);
        Object request = newShortToolRequest(requestClass,
                "{\"operation\":\"create\",\"sessionId\":\"9\",\"taskId\":\"77\",\"title\":\"新增侍从转述桥段\",\"description\":\"补齐密令传递链路\",\"sourceType\":\"PLANNING\",\"todoStatus\":\"TODO\"}",
                "trace-todo-create");

        Object result = handler.getClass().getMethod("execute", requestClass).invoke(handler, request);

        assertThat(resultClass.getMethod("status").invoke(result)).isEqualTo("SUCCESS");
        assertThat(String.valueOf(resultClass.getMethod("toolOutput").invoke(result)))
                .contains("\"operation\":\"create\"")
                .contains("\"todoId\":\"20041\"")
                .contains("\"title\":\"新增侍从转述桥段\"")
                .contains("\"todoStatus\":\"TODO\"");
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_SHOULD_LIST_TODOS_AND_RETURN_STRUCTURED_ITEMS() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Class<?> requestClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
        Class<?> resultClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallResult");
        Object serviceMock = org.mockito.Mockito.mock(serviceClass);
        Object todoA = instantiateNoArgsClass("com.penmate.backend.domain.todo.model.SessionTodo");
        writeField(todoA, "todoId", 20031L);
        writeField(todoA, "sessionId", 9L);
        writeField(todoA, "title", "修复密令来源");
        writeField(todoA, "sourceType", "QUALITY_REVIEW");
        writeField(todoA, "todoStatus", "BLOCKED");
        Object todoB = instantiateNoArgsClass("com.penmate.backend.domain.todo.model.SessionTodo");
        writeField(todoB, "todoId", 20032L);
        writeField(todoB, "sessionId", 9L);
        writeField(todoB, "title", "补充侍从转述桥段");
        writeField(todoB, "sourceType", "PLANNING");
        writeField(todoB, "todoStatus", "TODO");

        Method listMethod = serviceClass.getMethod("listSessionTodos", Long.class, Long.class, String.class);
        Object stubbingTarget = org.mockito.Mockito.doReturn(java.util.List.of(todoA, todoB)).when(serviceMock);
        listMethod.invoke(stubbingTarget, 1L, 9L, (String) null);

        Object handler = instantiateTodoCrudHandler(serviceMock);
        Object request = newShortToolRequest(requestClass,
                "{\"operation\":\"list\",\"sessionId\":\"9\"}",
                "trace-todo-list");

        Object result = handler.getClass().getMethod("execute", requestClass).invoke(handler, request);

        assertThat(resultClass.getMethod("status").invoke(result)).isEqualTo("SUCCESS");
        assertThat(String.valueOf(resultClass.getMethod("toolOutput").invoke(result)))
                .contains("\"operation\":\"list\"")
                .contains("\"items\"")
                .contains("\"todoId\":\"20031\"")
                .contains("\"todoId\":\"20032\"");
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_SHOULD_COMPLETE_TODO_AND_RETURN_STRUCTURED_OUTPUT() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Class<?> requestClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
        Class<?> resultClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallResult");
        Object serviceMock = org.mockito.Mockito.mock(serviceClass);
        Object completedTodo = instantiateNoArgsClass("com.penmate.backend.domain.todo.model.SessionTodo");
        writeField(completedTodo, "todoId", 20031L);
        writeField(completedTodo, "projectId", 1L);
        writeField(completedTodo, "sessionId", 9L);
        writeField(completedTodo, "taskId", 77L);
        writeField(completedTodo, "title", "修复密令来源");
        writeField(completedTodo, "sourceType", "QUALITY_REVIEW");
        writeField(completedTodo, "todoStatus", "DONE");
        writeField(completedTodo, "completedAt", LocalDateTime.of(2026, 5, 15, 22, 10, 0));

        Method completeMethod = serviceClass.getMethod("completeTodo", Long.class, Long.class, Long.class, Long.class, String.class);
        Object stubbingTarget = org.mockito.Mockito.doReturn(completedTodo).when(serviceMock);
        completeMethod.invoke(stubbingTarget, 1L, 9L, 20031L, 1001L, "trace-todo-complete");

        Object handler = instantiateTodoCrudHandler(serviceMock);
        Object request = newShortToolRequest(requestClass,
                "{\"operation\":\"complete\",\"sessionId\":\"9\",\"todoId\":\"20031\"}",
                "trace-todo-complete");

        Object result = handler.getClass().getMethod("execute", requestClass).invoke(handler, request);

        assertThat(resultClass.getMethod("status").invoke(result)).isEqualTo("SUCCESS");
        assertThat(String.valueOf(resultClass.getMethod("toolOutput").invoke(result)))
                .contains("\"operation\":\"complete\"")
                .contains("\"todoId\":\"20031\"")
                .contains("\"todoStatus\":\"DONE\"");
    }

    @Test
    void UT_APP_AGENT_TODO_CRUD_TOOL_HANDLER_SHOULD_DELETE_TODO_AND_RETURN_STRUCTURED_OUTPUT() throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Class<?> requestClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallRequest");
        Class<?> resultClass = loadClass("com.penmate.backend.application.agent.tool.runtime.ToolCallResult");
        Object serviceMock = org.mockito.Mockito.mock(serviceClass);

        Method deleteMethod = serviceClass.getMethod("deleteTodo", Long.class, Long.class, Long.class, Long.class, String.class);
        Object stubbingTarget = org.mockito.Mockito.doNothing().when(serviceMock);
        deleteMethod.invoke(stubbingTarget, 1L, 9L, 20031L, 1001L, "trace-todo-delete");

        Object handler = instantiateTodoCrudHandler(serviceMock);
        Object request = newShortToolRequest(requestClass,
                "{\"operation\":\"delete\",\"sessionId\":\"9\",\"todoId\":\"20031\"}",
                "trace-todo-delete");

        Object result = handler.getClass().getMethod("execute", requestClass).invoke(handler, request);

        assertThat(resultClass.getMethod("status").invoke(result)).isEqualTo("SUCCESS");
        assertThat(String.valueOf(resultClass.getMethod("toolOutput").invoke(result)))
                .contains("\"operation\":\"delete\"")
                .contains("\"todoId\":\"20031\"")
                .contains("\"deleted\":true");
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

    private static Object instantiateTodoCrudHandler(Object serviceMock) throws Exception {
        Class<?> handlerClass = loadClass("com.penmate.backend.application.agent.tool.handler.TodoCrudToolHandler");
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        for (Constructor<?> constructor : handlerClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] args = new Object[parameterTypes.length];
            boolean supported = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (serviceClass.equals(parameterTypes[i])) {
                    args[i] = serviceMock;
                } else if (!parameterTypes[i].isPrimitive()) {
                    args[i] = null;
                } else {
                    supported = false;
                    break;
                }
            }
            if (!supported) {
                continue;
            }
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        throw new AssertionError("Expected TodoCrudToolHandler to expose a supported constructor");
    }

    private static Object buildTodoCandidate() throws Exception {
        return buildTodoCandidateWith("修订第三章开场", "补齐密令来源桥段", "PLANNING", "BLOCKED");
    }

    private static Object buildTodoCandidateWith(String title, String description, String sourceType, String todoStatus) throws Exception {
        Object candidate = instantiateNoArgsClass("com.penmate.backend.domain.todo.model.SessionTodo");
        writeField(candidate, "title", title);
        writeField(candidate, "description", description);
        writeField(candidate, "sourceType", sourceType);
        writeField(candidate, "todoStatus", todoStatus);
        return candidate;
    }

    private static Object newShortToolRequest(Class<?> requestClass, String toolArgsJson, String traceId) throws Exception {
        return requestClass.getDeclaredConstructor(
                        Long.class,
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        Long.class,
                        String.class,
                        String.class,
                        String.class
                )
                .newInstance(
                        1L,
                        77L,
                        9L,
                        "todo_crud",
                        toolArgsJson,
                        1001L,
                        traceId,
                        "{}",
                        "todo-call-1"
                );
    }

    private static void writeField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
