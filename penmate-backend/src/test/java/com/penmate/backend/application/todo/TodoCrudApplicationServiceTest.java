package com.penmate.backend.application.todo;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TodoCrudApplicationServiceTest extends BaseApplicationServiceTest {

    private static final Long PROJECT_ID = 10001L;
    private static final Long SESSION_ID = 90001L;
    private static final Long SOURCE_RUN_ID = 80001L;
    private static final Long OPERATOR_ID = 70001L;
    private static final String TRACE_ID = "UT-TRACE-TODO-CRUD";

    @Test
    void UT_APP_TODO_CRUD_SHOULD_CREATE_BATCH_UPDATE_COMPLETE_SOFT_DELETE_AND_FILTER_BY_SESSION() throws Exception {
        TodoRepositoryStub repositoryStub = new TodoRepositoryStub();
        Object service = instantiateService(repositoryStub);

        Object created = invokeCreateTodo(service,
                new TodoPayload("修复第三章开场", "补齐主角出场动机", "USER_REQUEST", "TODO"));
        String createdTodoId = stringifyBusinessId(readProperty(created, "todoId", "id"));
        assertThat(createdTodoId).isNotBlank();
        assertThat(readProperty(created, "sessionId")).isEqualTo(SESSION_ID);
        assertThat(readProperty(created, "sourceRunId")).isEqualTo(SOURCE_RUN_ID);
        assertThat(readProperty(created, "title")).isEqualTo("修复第三章开场");
        assertThat(readProperty(created, "todoStatus", "status")).isEqualTo("TODO");

        List<?> batchCreated = invokeBatchCreate(service, List.of(
                new TodoPayload("同步人物设定", "把主角隐瞒原因同步到设定卡", "STORY_BIBLE_UPDATE", "TODO"),
                new TodoPayload("夜宴场景复查", "统一夜间灯火描写", "QUALITY_REVIEW", "IN_PROGRESS")
        ));
        assertThat(batchCreated).hasSize(2);

        Object updated = invokeUpdateTodo(service, createdTodoId,
                new TodoPayload("修订第三章开场", "补齐动机并前置冲突", "PLANNING", "BLOCKED"));
        assertThat(readProperty(updated, "title")).isEqualTo("修订第三章开场");
        assertThat(readProperty(updated, "todoStatus", "status")).isEqualTo("BLOCKED");
        assertThat(readProperty(updated, "sourceType")).isEqualTo("PLANNING");

        String batchTodoId = stringifyBusinessId(readProperty(batchCreated.get(0), "todoId", "id"));
        Object completed = invokeCompleteTodo(service, batchTodoId);
        assertThat(readProperty(completed, "todoStatus", "status")).isEqualTo("DONE");
        assertThat(readProperty(completed, "completedAt")).isNotNull();

        String deletedTodoId = stringifyBusinessId(readProperty(batchCreated.get(1), "todoId", "id"));
        invokeDeleteTodo(service, deletedTodoId);

        List<?> sessionTodos = invokeListTodos(service, null);
        assertThat(sessionTodos).hasSize(2);
        assertThat(sessionTodos)
                .extracting(item -> stringifyBusinessId(readProperty(item, "todoId", "id")))
                .containsExactly(createdTodoId, batchTodoId);

        List<?> doneTodos = invokeListTodos(service, "DONE");
        assertThat(doneTodos).hasSize(1);
        assertThat(stringifyBusinessId(readProperty(doneTodos.get(0), "todoId", "id"))).isEqualTo(batchTodoId);
        assertThat(readProperty(doneTodos.get(0), "todoStatus", "status")).isEqualTo("DONE");

        Map<String, Object> deletedRow = repositoryStub.snapshot(deletedTodoId);
        assertThat(deletedRow).isNotNull();
        assertThat(deletedRow.get("deletedAt")).isNotNull();
        assertThat(deletedRow.get("sessionId")).isEqualTo(SESSION_ID);
    }

    @Test
    void UT_APP_TODO_CRUD_SHOULD_MAP_WRITE_FAILURE_TO_STABLE_BUSINESS_EXCEPTION() throws Exception {
        TodoRepositoryStub repositoryStub = new TodoRepositoryStub();
        repositoryStub.failWritesWith(new RuntimeException("duplicate key on session todos"));
        Object service = instantiateService(repositoryStub);

        assertThatThrownBy(() -> invokeCreateTodo(service,
                new TodoPayload("创建失败待办", "模拟写库异常", "USER_REQUEST", "TODO")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("todo persistence failed");
    }

    @Test
    void UT_APP_TODO_CRUD_BATCH_CREATE_SHOULD_DECLARE_TRANSACTION_BOUNDARY() {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        Method method = requireMethod(serviceClass, "batchCreateTodos", "batchCreate", "createTodos");

        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void UT_APP_TODO_CRUD_SHOULD_PERSIST_ONLY_AUTO_CREATABLE_ITEMS_FROM_TODO_PLAN() throws Exception {
        TodoRepositoryStub repositoryStub = new TodoRepositoryStub();
        Object service = instantiateService(repositoryStub);

        Object todoPlan = buildTodoPlan(
                "第三章修订待办",
                "先修复逻辑漏洞再补人物动作",
                "创建第一项",
                List.of(
                        new TodoPlanItemPayload("修复密令来源", "补充侍从转述桥段", "P0", "QUALITY_REVIEW", "TODO", true, "避免剧情漏洞", List.of("密令来源明确")),
                        new TodoPlanItemPayload("人工确认设定变更", "等待编辑确认后再处理", "P2", "STORY_BIBLE_UPDATE", "BLOCKED", false, "需要人工审批", List.of("编辑完成确认"))
                )
        );

        List<?> created = invokePersistTodoPlan(service, todoPlan);

        assertThat(created).hasSize(1);
        assertThat(readProperty(created.get(0), "title")).isEqualTo("修复密令来源");
        assertThat(readProperty(created.get(0), "sourceType")).isEqualTo("QUALITY_REVIEW");
        assertThat(invokeListTodos(service, null)).hasSize(1);
    }

    @Test
    void UT_APP_TODO_CRUD_SHOULD_REJECT_INVALID_SOURCE_TYPE() throws Exception {
        TodoRepositoryStub repositoryStub = new TodoRepositoryStub();
        Object service = instantiateService(repositoryStub);

        assertThatThrownBy(() -> invokeCreateTodo(service,
                new TodoPayload("非法来源", "非法来源类型", "UNKNOWN", "TODO")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sourceType");
    }

    @Test
    void UT_APP_TODO_CRUD_SHOULD_REJECT_INVALID_TODO_STATUS() throws Exception {
        TodoRepositoryStub repositoryStub = new TodoRepositoryStub();
        Object service = instantiateService(repositoryStub);

        assertThatThrownBy(() -> invokeCreateTodo(service,
                new TodoPayload("非法状态", "非法状态类型", "USER_REQUEST", "LATER")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("todoStatus");
    }

    @Test
    void UT_APP_TODO_CRUD_SHOULD_PASS_SESSION_SCOPE_TO_REPOSITORY_WHEN_LISTING() throws Exception {
        TodoRepositoryStub repositoryStub = new TodoRepositoryStub();
        Object service = instantiateService(repositoryStub);
        Method listMethod = requireMethod(service.getClass(), "listSessionTodos", "listTodos", "listBySession");

        invoke(service, listMethod, new Object[]{321L, 654L, "DONE"});

        assertThat(repositoryStub.lastQueryProjectId).isEqualTo(321L);
        assertThat(repositoryStub.lastQuerySessionId).isEqualTo(654L);
        assertThat(repositoryStub.lastQueryStatus).isEqualTo("DONE");
    }

    private Object instantiateService(TodoRepositoryStub repositoryStub) throws Exception {
        Class<?> serviceClass = loadClass("com.penmate.backend.application.todo.TodoCrudApplicationService");
        List<Constructor<?>> constructors = Arrays.stream(serviceClass.getDeclaredConstructors())
                .sorted(Comparator.comparingInt((Constructor<?> candidate) -> candidate.getParameterCount()).reversed())
                .toList();
        for (Constructor<?> constructor : constructors) {
            Object[] args = resolveConstructorArguments(constructor, repositoryStub);
            if (args == null) {
                continue;
            }
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        throw new AssertionError("Expected a supported constructor on TodoCrudApplicationService");
    }

    private Object[] resolveConstructorArguments(Constructor<?> constructor, TodoRepositoryStub repositoryStub) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            String name = parameterType.getName();
            if (name.equals("com.penmate.backend.domain.todo.repository.SessionTodoRepository")) {
                args[i] = repositoryStub.createProxy(parameterType);
                continue;
            }
            if (name.equals("com.penmate.backend.domain.shared.service.BusinessIdGenerator")) {
                args[i] = createBusinessIdGeneratorProxy(parameterType);
                continue;
            }
            if (name.equals("com.penmate.backend.domain.shared.service.AuditService")) {
                args[i] = auditService;
                continue;
            }
            if (name.equals("com.penmate.backend.domain.shared.service.RealtimeEventService")) {
                args[i] = mock(parameterType);
                continue;
            }
            if (parameterType.isInterface()) {
                args[i] = mock(parameterType);
                continue;
            }
            Object defaultValue = instantiateWithNoArgs(parameterType);
            if (defaultValue != Unsupported.INSTANCE) {
                args[i] = defaultValue;
                continue;
            }
            if (parameterType.isPrimitive()) {
                args[i] = primitiveDefault(parameterType);
                continue;
            }
            return null;
        }
        return args;
    }

    private Object createBusinessIdGeneratorProxy(Class<?> businessIdGeneratorType) {
        AtomicLong sequence = new AtomicLong(200000L);
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            if (method.getName().equals("nextId")) {
                return sequence.getAndIncrement();
            }
            return defaultValue(method.getReturnType());
        };
        return Proxy.newProxyInstance(
                businessIdGeneratorType.getClassLoader(),
                new Class[]{businessIdGeneratorType},
                handler
        );
    }

    private Object invokeCreateTodo(Object service, TodoPayload payload) throws Exception {
        Method method = requireMethod(service.getClass(), "createTodo", "create", "createSessionTodo");
        return invoke(service, method, buildArguments(method, Operation.CREATE, null, payload, null));
    }

    private List<?> invokeBatchCreate(Object service, List<TodoPayload> payloads) throws Exception {
        Method method = requireMethod(service.getClass(), "batchCreateTodos", "batchCreate", "createTodos");
        Object result = invoke(service, method, buildArguments(method, Operation.BATCH_CREATE, null, payloads, null));
        if (result instanceof List<?> list) {
            return list;
        }
        throw new AssertionError("Expected batch create to return a list");
    }

    private Object invokeUpdateTodo(Object service, String todoId, TodoPayload payload) throws Exception {
        Method method = requireMethod(service.getClass(), "updateTodo", "update", "updateSessionTodo");
        return invoke(service, method, buildArguments(method, Operation.UPDATE, todoId, payload, null));
    }

    private Object invokeCompleteTodo(Object service, String todoId) throws Exception {
        Method method = requireMethod(service.getClass(), "completeTodo", "complete", "completeSessionTodo");
        return invoke(service, method, buildArguments(method, Operation.COMPLETE, todoId, null, null));
    }

    private void invokeDeleteTodo(Object service, String todoId) throws Exception {
        Method method = requireMethod(service.getClass(), "deleteTodo", "delete", "softDeleteTodo", "removeTodo");
        invoke(service, method, buildArguments(method, Operation.DELETE, todoId, null, null));
    }

    private List<?> invokeListTodos(Object service, String statusFilter) throws Exception {
        Method method = requireMethod(service.getClass(), "listSessionTodos", "listTodos", "listBySession");
        Object result = invoke(service, method, buildArguments(method, Operation.LIST, null, null, statusFilter));
        if (result instanceof List<?> list) {
            return list;
        }
        throw new AssertionError("Expected listTodos to return a list");
    }

    private List<?> invokePersistTodoPlan(Object service, Object todoPlan) throws Exception {
        Method method = requireMethod(service.getClass(), "persistTodoPlan", "createTodosFromPlan", "batchCreateFromPlan", "saveTodoPlan");
        Object result = invoke(service, method, buildPlanArguments(method, todoPlan));
        if (result instanceof List<?> list) {
            return list;
        }
        throw new AssertionError("Expected todo plan persistence to return a list");
    }

    private Object[] buildPlanArguments(Method method, Object todoPlan) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        int longIndex = 0;
        int stringIndex = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            String parameterTypeName = parameterType.getName();
            if (parameterType.equals(Long.class) || parameterType.equals(long.class)) {
                args[i] = switch (longIndex++) {
                    case 0 -> PROJECT_ID;
                    case 1 -> SESSION_ID;
                    case 2 -> SOURCE_RUN_ID;
                    default -> OPERATOR_ID;
                };
                continue;
            }
            if (parameterType.equals(String.class)) {
                args[i] = stringIndex++ == 0 ? TRACE_ID : TRACE_ID;
                continue;
            }
            if (parameterTypeName.equals("com.penmate.backend.application.todo.TodoPlanView")) {
                args[i] = todoPlan;
                continue;
            }
            if (parameterType.isPrimitive()) {
                args[i] = primitiveDefault(parameterType);
                continue;
            }
            args[i] = null;
        }
        return args;
    }

    private Object buildTodoPlan(String planTitle,
                                 String planSummary,
                                 String recommendedNextAction,
                                 List<TodoPlanItemPayload> items) throws Exception {
        Class<?> itemClass = loadClass("com.penmate.backend.application.todo.TodoPlanItemView");
        Constructor<?> itemConstructor = itemClass.getDeclaredConstructor(
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                String.class,
                List.class,
                List.class
        );
        itemConstructor.setAccessible(true);
        List<Object> builtItems = new ArrayList<>();
        for (TodoPlanItemPayload item : items) {
            builtItems.add(itemConstructor.newInstance(
                    item.title(),
                    item.description(),
                    item.priority(),
                    item.sourceType(),
                    item.recommendedStatus(),
                    item.suggestedAutoCreate(),
                    item.rationale(),
                    item.acceptanceCriteria(),
                    List.of()
            ));
        }
        Class<?> planClass = loadClass("com.penmate.backend.application.todo.TodoPlanView");
        Constructor<?> planConstructor = planClass.getDeclaredConstructor(String.class, String.class, String.class, List.class);
        planConstructor.setAccessible(true);
        return planConstructor.newInstance(planTitle, planSummary, recommendedNextAction, builtItems);
    }

    private Object[] buildArguments(Method method,
                                    Operation operation,
                                    String todoId,
                                    Object payload,
                                    String statusFilter) throws Exception {
        Class<?>[] parameterTypes = method.getParameterTypes();
        java.lang.reflect.Type[] genericParameterTypes = method.getGenericParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        int longIndex = 0;
        int stringIndex = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType.equals(Long.class) || parameterType.equals(long.class)) {
                args[i] = longArgument(operation, longIndex++, todoId);
                continue;
            }
            if (parameterType.equals(String.class)) {
                args[i] = stringArgument(operation, stringIndex++, todoId, payload, statusFilter, i == parameterTypes.length - 1);
                continue;
            }
            if (List.class.isAssignableFrom(parameterType) || Collection.class.isAssignableFrom(parameterType)) {
                args[i] = listArgument(operation, genericParameterTypes[i], payload);
                continue;
            }
            if (parameterType.isPrimitive()) {
                args[i] = primitiveDefault(parameterType);
                continue;
            }
            args[i] = complexArgument(parameterType, operation, todoId, payload);
        }
        return args;
    }

    private Long longArgument(Operation operation, int longIndex, String todoId) {
        return switch (operation) {
            case CREATE -> switch (longIndex) {
                case 0 -> PROJECT_ID;
                case 1 -> SESSION_ID;
                case 2 -> SOURCE_RUN_ID;
                default -> OPERATOR_ID;
            };
            case BATCH_CREATE -> switch (longIndex) {
                case 0 -> PROJECT_ID;
                case 1 -> SESSION_ID;
                case 2 -> SOURCE_RUN_ID;
                default -> OPERATOR_ID;
            };
            case UPDATE -> switch (longIndex) {
                case 0 -> PROJECT_ID;
                case 1 -> SESSION_ID;
                case 2 -> Long.valueOf(todoId);
                case 3 -> SOURCE_RUN_ID;
                default -> OPERATOR_ID;
            };
            case COMPLETE, DELETE -> switch (longIndex) {
                case 0 -> PROJECT_ID;
                case 1 -> SESSION_ID;
                case 2 -> Long.valueOf(todoId);
                default -> OPERATOR_ID;
            };
            case LIST -> switch (longIndex) {
                case 0 -> PROJECT_ID;
                default -> SESSION_ID;
            };
        };
    }

    private String stringArgument(Operation operation,
                                  int stringIndex,
                                  String todoId,
                                  Object payload,
                                  String statusFilter,
                                  boolean lastParameter) {
        if (operation == Operation.LIST) {
            return statusFilter;
        }
        if (lastParameter) {
            return TRACE_ID;
        }
        if (operation == Operation.UPDATE || operation == Operation.COMPLETE || operation == Operation.DELETE) {
            if (stringIndex == 0 && todoId != null) {
                return todoId;
            }
        }
        if (payload instanceof TodoPayload todoPayload) {
            return switch (stringIndex) {
                case 0 -> todoPayload.title();
                case 1 -> todoPayload.description();
                case 2 -> todoPayload.sourceType();
                default -> todoPayload.todoStatus();
            };
        }
        return TRACE_ID;
    }

    private Object listArgument(Operation operation, java.lang.reflect.Type genericType, Object payload) throws Exception {
        if (operation != Operation.BATCH_CREATE) {
            return List.of();
        }
        if (!(payload instanceof List<?> payloads)) {
            return List.of();
        }
        if (genericType instanceof ParameterizedType parameterizedType) {
            java.lang.reflect.Type actualType = parameterizedType.getActualTypeArguments()[0];
            if (actualType instanceof Class<?> itemType) {
                List<Object> items = new ArrayList<>();
                for (Object item : payloads) {
                    items.add(complexArgument(itemType, Operation.CREATE, null, item));
                }
                return items;
            }
        }
        return payloads;
    }

    private Object complexArgument(Class<?> parameterType,
                                   Operation operation,
                                   String todoId,
                                   Object payload) throws Exception {
        Object instance = instantiateWithNoArgs(parameterType);
        if (instance == Unsupported.INSTANCE) {
            throw new AssertionError("Unsupported parameter type for TodoCrudApplicationService method: " + parameterType.getName());
        }
        if (instance == null) {
            return null;
        }
        TodoPayload todoPayload = payload instanceof TodoPayload todo ? todo : null;
        writePropertyIfPresent(instance, "projectId", PROJECT_ID);
        writePropertyIfPresent(instance, "sessionId", SESSION_ID);
        writePropertyIfPresent(instance, "sourceRunId", SOURCE_RUN_ID);
        writePropertyIfPresent(instance, "operatorId", OPERATOR_ID);
        writePropertyIfPresent(instance, "traceId", TRACE_ID);
        if (todoId != null) {
            writePropertyIfPresent(instance, "todoId", todoId);
            writePropertyIfPresent(instance, "id", Long.valueOf(todoId));
        }
        if (todoPayload != null) {
            writePropertyIfPresent(instance, "title", todoPayload.title());
            writePropertyIfPresent(instance, "description", todoPayload.description());
            writePropertyIfPresent(instance, "sourceType", todoPayload.sourceType());
            writePropertyIfPresent(instance, "todoStatus", todoPayload.todoStatus());
            writePropertyIfPresent(instance, "status", todoPayload.todoStatus());
            writePropertyIfPresent(instance, "acceptanceCriteria", List.of("完成后可直接进入下一步"));
            writePropertyIfPresent(instance, "dependsOn", List.of());
        }
        if (operation == Operation.COMPLETE) {
            writePropertyIfPresent(instance, "todoStatus", "DONE");
            writePropertyIfPresent(instance, "status", "DONE");
        }
        return instance;
    }

    private Method requireMethod(Class<?> type, String... candidates) {
        for (String candidate : candidates) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(candidate)) {
                    return method;
                }
            }
        }
        throw new AssertionError("Expected one of methods to exist on " + type.getName() + ": " + Arrays.toString(candidates));
    }

    private Object invoke(Object target, Method method, Object[] args) throws Exception {
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

    private Class<?> loadClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected class to exist: " + fqcn, ex);
        }
    }

    private Object instantiateWithNoArgs(Class<?> type) {
        if (type.equals(String.class)) {
            return "";
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length == 0 ? null : constants[0];
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            return Unsupported.INSTANCE;
        }
    }

    private Object readProperty(Object target, String... names) {
        for (String name : names) {
            if (target instanceof Map<?, ?> map && map.containsKey(name)) {
                return map.get(name);
            }
            try {
                Method accessor = target.getClass().getMethod(name);
                accessor.setAccessible(true);
                return accessor.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // continue
            }
            try {
                Method getter = target.getClass().getMethod("get" + capitalize(name));
                getter.setAccessible(true);
                return getter.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // continue
            }
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                // continue
            }
        }
        return null;
    }

    private void writePropertyIfPresent(Object target, String name, Object value) {
        if (target == null) {
            return;
        }
        String setterName = "set" + capitalize(name);
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                try {
                    method.setAccessible(true);
                    method.invoke(target, convertValue(value, method.getParameterTypes()[0]));
                    return;
                } catch (ReflectiveOperationException ignored) {
                    return;
                }
            }
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, convertValue(value, field.getType()));
        } catch (ReflectiveOperationException ignored) {
            // ignore on purpose
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
        if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) {
            return Boolean.valueOf(String.valueOf(value));
        }
        if (List.class.isAssignableFrom(targetType) && value instanceof List<?> list) {
            return list;
        }
        return value;
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

    private String stringifyBusinessId(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private enum Operation {
        CREATE,
        BATCH_CREATE,
        UPDATE,
        COMPLETE,
        DELETE,
        LIST
    }

    private enum Unsupported {
        INSTANCE
    }

    private record TodoPayload(String title, String description, String sourceType, String todoStatus) {
    }

    private record TodoPlanItemPayload(String title,
                                       String description,
                                       String priority,
                                       String sourceType,
                                       String recommendedStatus,
                                       boolean suggestedAutoCreate,
                                       String rationale,
                                       List<String> acceptanceCriteria) {
    }

    private final class TodoRepositoryStub implements InvocationHandler {

        private final AtomicLong physicalIdSequence = new AtomicLong(1L);
        private final Map<String, Map<String, Object>> rowsByTodoId = new LinkedHashMap<>();
        private RuntimeException writeFailure;
        private Long lastQueryProjectId;
        private Long lastQuerySessionId;
        private String lastQueryStatus;

        Object createProxy(Class<?> repositoryType) {
            return Proxy.newProxyInstance(
                    repositoryType.getClassLoader(),
                    new Class[]{repositoryType},
                    this
            );
        }

        void failWritesWith(RuntimeException failure) {
            this.writeFailure = failure;
        }

        Map<String, Object> snapshot(String todoId) {
            return rowsByTodoId.get(todoId);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            String methodName = method.getName();
            String normalizedMethodName = methodName.toLowerCase(Locale.ROOT);
            if (isWriteMethod(normalizedMethodName)) {
                ensureWriteAllowed();
            }
            if (normalizedMethodName.contains("batch") && args != null) {
                return handleBatchMethod(method, args);
            }
            if (normalizedMethodName.startsWith("insert") || normalizedMethodName.startsWith("save")) {
                Map<String, Object> row = upsertTodoRow(extractTodoArgument(args));
                return adaptWriteReturn(method.getReturnType(), row, 1);
            }
            if (normalizedMethodName.startsWith("update")) {
                Map<String, Object> row = upsertTodoRow(extractTodoArgument(args));
                return adaptWriteReturn(method.getReturnType(), row, 1);
            }
            if (normalizedMethodName.contains("complete")) {
                Map<String, Object> row = findMutableRow(args);
                if (row != null) {
                    row.put("todoStatus", "DONE");
                    row.put("completedAt", LocalDateTime.now());
                    row.put("updatedAt", LocalDateTime.now());
                }
                return adaptWriteReturn(method.getReturnType(), row, row == null ? 0 : 1);
            }
            if (normalizedMethodName.contains("delete")) {
                Map<String, Object> row = findMutableRow(args);
                if (row != null) {
                    row.put("deletedAt", LocalDateTime.now());
                    row.put("updatedAt", LocalDateTime.now());
                }
                return adaptWriteReturn(method.getReturnType(), row, row == null ? 0 : 1);
            }
            if (List.class.isAssignableFrom(method.getReturnType())) {
                return listRows(args);
            }
            if (normalizedMethodName.startsWith("find") || normalizedMethodName.startsWith("get")) {
                Map<String, Object> row = findRow(args);
                if (row == null) {
                    return null;
                }
                return materializeTodo(row);
            }
            return defaultValue(method.getReturnType());
        }

        private Object handleBatchMethod(Method method, Object[] args) throws Exception {
            List<?> listArg = Arrays.stream(args)
                    .filter(List.class::isInstance)
                    .map(List.class::cast)
                    .findFirst()
                    .orElse(List.of());
            List<Object> materialized = new ArrayList<>();
            for (Object item : listArg) {
                materialized.add(materializeAndStore(item));
            }
            if (List.class.isAssignableFrom(method.getReturnType())) {
                return materialized;
            }
            if (method.getReturnType().equals(int.class) || method.getReturnType().equals(Integer.class)) {
                return materialized.size();
            }
            return defaultValue(method.getReturnType());
        }

        private boolean isWriteMethod(String normalizedMethodName) {
            return normalizedMethodName.startsWith("insert")
                    || normalizedMethodName.startsWith("save")
                    || normalizedMethodName.startsWith("update")
                    || normalizedMethodName.contains("delete")
                    || normalizedMethodName.contains("complete")
                    || normalizedMethodName.contains("batch");
        }

        private void ensureWriteAllowed() {
            if (writeFailure != null) {
                throw writeFailure;
            }
        }

        private Object materializeAndStore(Object todoLike) throws Exception {
            Map<String, Object> row = upsertTodoRow(todoLike);
            return materializeTodo(row);
        }

        private Map<String, Object> upsertTodoRow(Object todoLike) {
            Map<String, Object> row = extractTodoRow(todoLike);
            if (row.get("todoId") == null) {
                row.put("todoId", String.valueOf(200000L + rowsByTodoId.size()));
            }
            row.putIfAbsent("id", physicalIdSequence.getAndIncrement());
            row.putIfAbsent("projectId", PROJECT_ID);
            row.putIfAbsent("sessionId", SESSION_ID);
            row.putIfAbsent("sourceRunId", SOURCE_RUN_ID);
            row.putIfAbsent("title", "未命名待办");
            row.putIfAbsent("description", "");
            row.putIfAbsent("sourceType", "USER_REQUEST");
            row.putIfAbsent("todoStatus", "TODO");
            row.put("updatedAt", LocalDateTime.now());
            row.putIfAbsent("createdAt", LocalDateTime.now());
            rowsByTodoId.put(String.valueOf(row.get("todoId")), row);
            return row;
        }

        private Map<String, Object> extractTodoRow(Object todoLike) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (todoLike == null) {
                return row;
            }
            row.put("id", readProperty(todoLike, "id"));
            row.put("todoId", readProperty(todoLike, "todoId", "id"));
            row.put("projectId", normalizeLong(readProperty(todoLike, "projectId"), PROJECT_ID));
            row.put("sessionId", normalizeLong(readProperty(todoLike, "sessionId"), SESSION_ID));
            row.put("sourceRunId", normalizeLong(readProperty(todoLike, "sourceRunId"), SOURCE_RUN_ID));
            row.put("title", readProperty(todoLike, "title"));
            row.put("description", readProperty(todoLike, "description"));
            row.put("sourceType", readProperty(todoLike, "sourceType"));
            Object status = readProperty(todoLike, "todoStatus", "status");
            row.put("todoStatus", status == null ? "TODO" : String.valueOf(status));
            Object completedAt = readProperty(todoLike, "completedAt");
            if (completedAt != null) {
                row.put("completedAt", completedAt);
            }
            Object deletedAt = readProperty(todoLike, "deletedAt");
            if (deletedAt != null) {
                row.put("deletedAt", deletedAt);
            }
            return row;
        }

        private Object extractTodoArgument(Object[] args) {
            if (args == null) {
                return null;
            }
            return Arrays.stream(args)
                    .filter(Objects::nonNull)
                    .filter(arg -> arg.getClass().getName().contains("Todo") || !(arg instanceof Number) && !(arg instanceof String))
                    .findFirst()
                    .orElse(null);
        }

        private Map<String, Object> findMutableRow(Object[] args) {
            String todoId = findTodoId(args);
            return todoId == null ? null : rowsByTodoId.get(todoId);
        }

        private Map<String, Object> findRow(Object[] args) {
            String todoId = findTodoId(args);
            if (todoId != null && rowsByTodoId.containsKey(todoId)) {
                return rowsByTodoId.get(todoId);
            }
            List<Map<String, Object>> rows = visibleRows(args, null);
            return rows.isEmpty() ? null : rows.get(0);
        }

        private List<Object> listRows(Object[] args) throws Exception {
            captureQueryScope(args);
            String status = findStatus(args);
            List<Map<String, Object>> rows = visibleRows(args, status);
            List<Object> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                result.add(materializeTodo(row));
            }
            return result;
        }

        private void captureQueryScope(Object[] args) {
            if (args == null) {
                return;
            }
            int numericIndex = 0;
            for (Object arg : args) {
                if (arg instanceof Number number) {
                    if (numericIndex == 0) {
                        lastQueryProjectId = number.longValue();
                    } else if (numericIndex == 1) {
                        lastQuerySessionId = number.longValue();
                    }
                    numericIndex++;
                    continue;
                }
                if (arg instanceof String stringValue
                        && List.of("TODO", "IN_PROGRESS", "BLOCKED", "DONE").contains(stringValue)) {
                    lastQueryStatus = stringValue;
                }
            }
        }

        private List<Map<String, Object>> visibleRows(Object[] args, String status) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rowsByTodoId.values()) {
                if (row.get("deletedAt") != null) {
                    continue;
                }
                if (!Objects.equals(row.get("sessionId"), SESSION_ID)) {
                    continue;
                }
                if (status != null && !status.equals(row.get("todoStatus"))) {
                    continue;
                }
                result.add(row);
            }
            return result;
        }

        private String findTodoId(Object[] args) {
            if (args == null) {
                return null;
            }
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof String str && rowsByTodoId.containsKey(str)) {
                    return str;
                }
                if (arg instanceof Number number) {
                    String candidate = String.valueOf(number.longValue());
                    if (rowsByTodoId.containsKey(candidate)) {
                        return candidate;
                    }
                }
                Object nestedTodoId = readProperty(arg, "todoId", "id");
                if (nestedTodoId != null) {
                    String candidate = String.valueOf(nestedTodoId);
                    if (rowsByTodoId.containsKey(candidate)) {
                        return candidate;
                    }
                }
            }
            return null;
        }

        private String findStatus(Object[] args) {
            if (args == null) {
                return null;
            }
            return Arrays.stream(args)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(Objects::nonNull)
                    .filter(value -> List.of("TODO", "IN_PROGRESS", "BLOCKED", "DONE").contains(value))
                    .findFirst()
                    .orElse(null);
        }

        private Object materializeTodo(Map<String, Object> row) throws Exception {
            Class<?> todoClass = loadClass("com.penmate.backend.domain.todo.model.SessionTodo");
            Object todo = instantiateWithNoArgs(todoClass);
            if (todo == Unsupported.INSTANCE) {
                throw new AssertionError("Expected SessionTodo to provide a no-args constructor");
            }
            writePropertyIfPresent(todo, "id", row.get("id"));
            writePropertyIfPresent(todo, "todoId", row.get("todoId"));
            writePropertyIfPresent(todo, "projectId", row.get("projectId"));
            writePropertyIfPresent(todo, "sessionId", row.get("sessionId"));
            writePropertyIfPresent(todo, "sourceRunId", row.get("sourceRunId"));
            writePropertyIfPresent(todo, "title", row.get("title"));
            writePropertyIfPresent(todo, "description", row.get("description"));
            writePropertyIfPresent(todo, "sourceType", row.get("sourceType"));
            writePropertyIfPresent(todo, "todoStatus", row.get("todoStatus"));
            writePropertyIfPresent(todo, "status", row.get("todoStatus"));
            writePropertyIfPresent(todo, "createdAt", row.get("createdAt"));
            writePropertyIfPresent(todo, "updatedAt", row.get("updatedAt"));
            writePropertyIfPresent(todo, "completedAt", row.get("completedAt"));
            writePropertyIfPresent(todo, "deletedAt", row.get("deletedAt"));
            return todo;
        }

        private Object adaptWriteReturn(Class<?> returnType, Map<String, Object> row, int affectedRows) throws Exception {
            if (returnType.equals(void.class)) {
                return null;
            }
            if (returnType.equals(int.class) || returnType.equals(Integer.class)) {
                return affectedRows;
            }
            if (returnType.equals(boolean.class) || returnType.equals(Boolean.class)) {
                return affectedRows > 0;
            }
            if (row == null) {
                return null;
            }
            return materializeTodo(row);
        }

        private Long normalizeLong(Object value, Long fallback) {
            if (value == null) {
                return fallback;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.valueOf(String.valueOf(value));
        }
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType.equals(void.class)) {
            return null;
        }
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.equals(int.class)) {
            return 0;
        }
        if (returnType.equals(long.class)) {
            return 0L;
        }
        if (returnType.equals(double.class)) {
            return 0D;
        }
        if (returnType.equals(float.class)) {
            return 0F;
        }
        if (returnType.equals(short.class)) {
            return (short) 0;
        }
        if (returnType.equals(byte.class)) {
            return (byte) 0;
        }
        if (returnType.equals(char.class)) {
            return (char) 0;
        }
        return null;
    }
}
