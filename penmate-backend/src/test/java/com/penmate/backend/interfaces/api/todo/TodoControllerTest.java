package com.penmate.backend.interfaces.api.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TodoControllerTest {

    private static final String BASE_PATH = "/api/v1/novels/10001/agent/sessions/90001/todos";
    private static final String TRACE_ID = "UT-TRACE-TODO-CONTROLLER";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void UT_TODO_LIST_SUCCESS() throws Exception {
        Object service = todoCrudServiceMock();
        stubListTodos(service, List.of(
                todo("20001", "修复第三章冲突", "QUALITY_REVIEW", "TODO"),
                todo("20002", "同步设定卡", "STORY_BIBLE_UPDATE", "IN_PROGRESS")
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get(BASE_PATH)
                        .param("status", "TODO")
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].todoId").value("20001"))
                .andExpect(jsonPath("$.data[0].sessionId").value("90001"))
                .andExpect(jsonPath("$.data[0].todoStatus").value("TODO"))
                .andExpect(jsonPath("$.meta.traceId").value(TRACE_ID));
    }

    @Test
    void UT_TODO_CREATE_SUCCESS() throws Exception {
        Object service = todoCrudServiceMock();
        stubSingleTodo(service, "createTodo", todo("20011", "整理待办", "USER_REQUEST", "TODO"));
        stubSingleTodo(service, "create", todo("20011", "整理待办", "USER_REQUEST", "TODO"));
        stubSingleTodo(service, "createSessionTodo", todo("20011", "整理待办", "USER_REQUEST", "TODO"));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post(BASE_PATH)
                        .param("operatorId", "70001")
                        .param("taskId", "80001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", TRACE_ID)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "整理待办",
                                "description", "把质量问题转成执行项",
                                "sourceType", "USER_REQUEST",
                                "todoStatus", "TODO"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoId").value("20011"))
                .andExpect(jsonPath("$.data.projectId").value("10001"))
                .andExpect(jsonPath("$.data.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.todoStatus").value("TODO"))
                .andExpect(jsonPath("$.meta.traceId").value(TRACE_ID));
    }

    @Test
    void UT_TODO_CREATE_WITHOUT_TASK_ID_SUCCESS() throws Exception {
        Object service = todoCrudServiceMock();
        stubSingleTodo(service, "createTodo", todo("20012", "用户直接创建待办", "USER_REQUEST", "TODO"));
        stubSingleTodo(service, "create", todo("20012", "用户直接创建待办", "USER_REQUEST", "TODO"));
        stubSingleTodo(service, "createSessionTodo", todo("20012", "用户直接创建待办", "USER_REQUEST", "TODO"));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post(BASE_PATH)
                        .param("operatorId", "70001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", TRACE_ID)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "用户直接创建待办",
                                "description", "不绑定 taskId 也可以创建",
                                "sourceType", "USER_REQUEST",
                                "todoStatus", "TODO"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoId").value("20012"))
                .andExpect(jsonPath("$.meta.traceId").value(TRACE_ID));
    }

    @Test
    void UT_TODO_BATCH_CREATE_SUCCESS() throws Exception {
        Object service = todoCrudServiceMock();
        stubBatchCreate(service, List.of(
                todo("20021", "修复设定冲突", "QUALITY_REVIEW", "TODO"),
                todo("20022", "补充伏笔说明", "PLANNING", "TODO")
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post(BASE_PATH + "/batch")
                        .param("operatorId", "70001")
                        .param("taskId", "80001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", TRACE_ID)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of(
                                        "title", "修复设定冲突",
                                        "description", "统一角色关系说明",
                                        "sourceType", "QUALITY_REVIEW",
                                        "todoStatus", "TODO"
                                ),
                                Map.of(
                                        "title", "补充伏笔说明",
                                        "description", "整理下一章伏笔",
                                        "sourceType", "PLANNING",
                                        "todoStatus", "TODO"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].todoId").value("20021"))
                .andExpect(jsonPath("$.data[1].todoId").value("20022"))
                .andExpect(jsonPath("$.meta.traceId").value(TRACE_ID));
    }

    @Test
    void UT_TODO_BATCH_CREATE_WITHOUT_TASK_ID_SUCCESS() throws Exception {
        Object service = todoCrudServiceMock();
        stubBatchCreate(service, List.of(
                todo("20023", "无 taskId 待办 A", "USER_REQUEST", "TODO"),
                todo("20024", "无 taskId 待办 B", "PLANNING", "TODO")
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post(BASE_PATH + "/batch")
                        .param("operatorId", "70001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", TRACE_ID)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of(
                                        "title", "无 taskId 待办 A",
                                        "description", "直接创建",
                                        "sourceType", "USER_REQUEST",
                                        "todoStatus", "TODO"
                                ),
                                Map.of(
                                        "title", "无 taskId 待办 B",
                                        "description", "直接批量创建",
                                        "sourceType", "PLANNING",
                                        "todoStatus", "TODO"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].todoId").value("20023"))
                .andExpect(jsonPath("$.data[1].todoId").value("20024"))
                .andExpect(jsonPath("$.meta.traceId").value(TRACE_ID));
    }

    @Test
    void UT_TODO_UPDATE_SUCCESS() throws Exception {
        Object service = todoCrudServiceMock();
        stubSingleTodo(service, "updateTodo", todo("20031", "修订第三章开场", "PLANNING", "BLOCKED"));
        stubSingleTodo(service, "update", todo("20031", "修订第三章开场", "PLANNING", "BLOCKED"));
        stubSingleTodo(service, "updateSessionTodo", todo("20031", "修订第三章开场", "PLANNING", "BLOCKED"));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(put(BASE_PATH + "/20031")
                        .param("operatorId", "70001")
                        .param("taskId", "80001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", TRACE_ID)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "修订第三章开场",
                                "description", "前置冲突并补齐动机",
                                "sourceType", "PLANNING",
                                "todoStatus", "BLOCKED"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoId").value("20031"))
                .andExpect(jsonPath("$.data.todoStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.meta.traceId").value(TRACE_ID));
    }

    @Test
    void UT_TODO_COMPLETE_SUCCESS() throws Exception {
        Object service = todoCrudServiceMock();
        stubSingleTodo(service, "completeTodo", completedTodo("20041", "完成质量修复"));
        stubSingleTodo(service, "complete", completedTodo("20041", "完成质量修复"));
        stubSingleTodo(service, "completeSessionTodo", completedTodo("20041", "完成质量修复"));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post(BASE_PATH + "/20041/complete")
                        .param("operatorId", "70001")
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoId").value("20041"))
                .andExpect(jsonPath("$.data.todoStatus").value("DONE"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.meta.traceId").value(TRACE_ID));
    }

    @Test
    void UT_TODO_DELETE_SUCCESS() throws Exception {
        Object service = todoCrudServiceMock();
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(delete(BASE_PATH + "/20051")
                        .param("operatorId", "70001")
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"))
                .andExpect(jsonPath("$.meta.traceId").value(TRACE_ID));
    }

    @Test
    void UT_TODO_CREATE_INVALID_PARAM() throws Exception {
        Object service = todoCrudServiceMock();
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post(BASE_PATH)
                        .param("operatorId", "70001")
                        .param("taskId", "80001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", TRACE_ID)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "",
                                "description", "缺少标题",
                                "sourceType", "USER_REQUEST",
                                "todoStatus", "TODO"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.traceId").value(TRACE_ID));
    }

    @Test
    void UT_TODO_REJECTS_LEGACY_PREFIX_IDS() throws Exception {
        Object service = todoCrudServiceMock();
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/v1/novels/project-10001/agent/sessions/90001/todos")
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    private MockMvc mockMvc(Object todoCrudApplicationService) throws Exception {
        Object controller = instantiateController(todoCrudApplicationService);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Object instantiateController(Object todoCrudApplicationService) {
        Class<?> controllerClass = loadClass("com.penmate.backend.interfaces.api.todo.TodoController");
        List<Constructor<?>> constructors = Arrays.stream(controllerClass.getDeclaredConstructors())
                .sorted(Comparator.comparingInt((Constructor<?> candidate) -> candidate.getParameterCount()).reversed())
                .toList();
        for (Constructor<?> constructor : constructors) {
            Object[] args = new Object[constructor.getParameterCount()];
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            boolean supported = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if (parameterType.getName().equals("com.penmate.backend.application.todo.TodoCrudApplicationService")) {
                    args[i] = todoCrudApplicationService;
                    continue;
                }
                args[i] = mock(parameterType);
            }
            if (!supported) {
                continue;
            }
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("Failed to instantiate TodoController", ex);
            }
        }
        throw new AssertionError("Expected TodoController constructor to be available");
    }

    private Object todoCrudServiceMock() {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        return mock(serviceClass);
    }

    private void stubListTodos(Object service, List<Object> todos) throws Exception {
        for (String methodName : List.of("listSessionTodos", "listTodos", "listBySession")) {
            Method method = findMethodIfExists(service.getClass(), methodName);
            if (method == null) {
                continue;
            }
            doReturn(todos).when(service);
            invokeReflectively(service, method, matcherArguments(method));
        }
    }

    private void stubBatchCreate(Object service, List<Object> todos) throws Exception {
        for (String methodName : List.of("batchCreateTodos", "batchCreate", "createTodos")) {
            Method method = findMethodIfExists(service.getClass(), methodName);
            if (method == null) {
                continue;
            }
            doReturn(todos).when(service);
            invokeReflectively(service, method, matcherArguments(method));
        }
    }

    private void stubSingleTodo(Object service, String methodName, Object todo) throws Exception {
        Method method = findMethodIfExists(service.getClass(), methodName);
        if (method == null) {
            return;
        }
        doReturn(todo).when(service);
        invokeReflectively(service, method, matcherArguments(method));
    }

    private Object[] matcherArguments(Method method) throws Exception {
        Object[] args = new Object[method.getParameterCount()];
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType.equals(Long.class)) {
                args[i] = nullable(Long.class);
            } else if (parameterType.equals(long.class)) {
                args[i] = anyLong();
            } else if (parameterType.equals(String.class)) {
                args[i] = anyString();
            } else if (List.class.isAssignableFrom(parameterType)) {
                args[i] = any(List.class);
            } else if (parameterType.isPrimitive()) {
                args[i] = primitiveDefault(parameterType);
            } else {
                args[i] = any(parameterType);
            }
        }
        return args;
    }

    private Object invokeReflectively(Object target, Method method, Object[] args) throws Exception {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            Throwable targetException = ex.getTargetException();
            if (targetException instanceof Exception exception) {
                throw exception;
            }
            if (targetException instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(targetException);
        }
    }

    private Object todo(String todoId, String title, String sourceType, String todoStatus) throws Exception {
        Object todo = instantiateWithNoArgs(loadClass("com.penmate.backend.domain.todo.model.SessionTodo"));
        if (todo == null) {
            throw new AssertionError("Expected SessionTodo to be instantiable");
        }
        write(todo, "todoId", Long.valueOf(todoId));
        write(todo, "projectId", 10001L);
        write(todo, "sessionId", 90001L);
        write(todo, "taskId", 80001L);
        write(todo, "title", title);
        write(todo, "description", title + " 的详细说明");
        write(todo, "sourceType", sourceType);
        write(todo, "todoStatus", todoStatus);
        write(todo, "createdAt", LocalDateTime.of(2026, 5, 14, 13, 0, 0));
        write(todo, "updatedAt", LocalDateTime.of(2026, 5, 14, 13, 5, 0));
        return todo;
    }

    private Object completedTodo(String todoId, String title) throws Exception {
        Object todo = todo(todoId, title, "QUALITY_REVIEW", "DONE");
        write(todo, "completedAt", LocalDateTime.of(2026, 5, 14, 13, 6, 0));
        return todo;
    }

    private void write(Object target, String fieldName, Object value) {
        String setterName = "set" + capitalize(fieldName);
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(setterName) || method.getParameterCount() != 1) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(target, convertValue(value, method.getParameterTypes()[0]));
                return;
            } catch (ReflectiveOperationException ignored) {
                return;
            }
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, convertValue(value, field.getType()));
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
    }

    private Object instantiateWithNoArgs(Class<?> type) throws Exception {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException ex) {
            return mock(type);
        }
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType.equals(Long.class) || targetType.equals(long.class)) {
            return Long.valueOf(String.valueOf(value));
        }
        if (targetType.equals(Integer.class) || targetType.equals(int.class)) {
            return Integer.valueOf(String.valueOf(value));
        }
        if (targetType.equals(String.class)) {
            return String.valueOf(value);
        }
        return value;
    }

    private Method findMethodIfExists(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private Class<?> loadClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected class to exist: " + fqcn, ex);
        }
    }

    private Object primitiveDefault(Class<?> primitiveType) {
        if (primitiveType.equals(boolean.class)) {
            return false;
        }
        if (primitiveType.equals(int.class)) {
            return 0;
        }
        if (primitiveType.equals(long.class)) {
            return 0L;
        }
        if (primitiveType.equals(double.class)) {
            return 0D;
        }
        if (primitiveType.equals(float.class)) {
            return 0F;
        }
        if (primitiveType.equals(short.class)) {
            return (short) 0;
        }
        if (primitiveType.equals(byte.class)) {
            return (byte) 0;
        }
        if (primitiveType.equals(char.class)) {
            return (char) 0;
        }
        return null;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
