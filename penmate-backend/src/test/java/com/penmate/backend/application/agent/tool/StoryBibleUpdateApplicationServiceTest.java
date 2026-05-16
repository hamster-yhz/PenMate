package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoryBibleUpdateApplicationServiceTest {

    @Test
    void UT_APP_AGENT_STORY_BIBLE_UPDATE_APPLICATION_SERVICE_EXECUTE_SHOULD_LIST_ENTRIES_BY_PROJECT_AND_CHAPTER() throws Exception {
        StoryBibleApplicationService storyBibleApplicationService = mock(StoryBibleApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskId(8001L);
        task.setProjectId(9001L);
        task.setUserId(1001L);
        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);
        StoryBibleEntry entry = new StoryBibleEntry();
        entry.setEntryId(88001L);
        entry.setProjectId(9001L);
        entry.setEntryKey("maid.secret_order");
        entry.setEntryType("character");
        entry.setTitle("侍从密令");
        entry.setContent("侍从负责转述密令。");
        entry.setCanonicalStatus("CANON");
        when(storyBibleApplicationService.listEntriesForChapter(9001L, 301L)).thenReturn(List.of(entry));

        Object service = instantiate(
                "com.penmate.backend.application.agent.tool.DefaultStoryBibleUpdateApplicationService",
                Map.of(
                        StoryBibleApplicationService.class, storyBibleApplicationService,
                        AgentRepository.class, agentRepository
                )
        );

        ToolCallResult result = execute(service, request("""
                {
                  "operation": "list",
                  "chapterId": 301
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput())
                .contains("maid.secret_order")
                .contains("侍从密令")
                .contains("CANON");
    }

    @Test
    void UT_APP_AGENT_STORY_BIBLE_UPDATE_APPLICATION_SERVICE_EXECUTE_SHOULD_REJECT_WHEN_TASK_DOES_NOT_BELONG_TO_CURRENT_OPERATOR() throws Exception {
        StoryBibleApplicationService storyBibleApplicationService = mock(StoryBibleApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskId(8001L);
        task.setProjectId(9001L);
        task.setUserId(2002L);
        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);

        Object service = instantiate(
                "com.penmate.backend.application.agent.tool.DefaultStoryBibleUpdateApplicationService",
                Map.of(
                        StoryBibleApplicationService.class, storyBibleApplicationService,
                        AgentRepository.class, agentRepository
                )
        );

        try {
            execute(service, request("""
                    {
                      "operation": "list",
                      "chapterId": 301
                    }
                    """));
        } catch (Exception ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            assertThat(String.valueOf(cause.getMessage())).contains("operator").contains("task");
            return;
        }
        throw new AssertionError("Expected task ownership guard to reject mismatched operator");
    }

    @Test
    void UT_APP_AGENT_STORY_BIBLE_UPDATE_APPLICATION_SERVICE_EXECUTE_SHOULD_CREATE_STRUCTURED_ENTRY_WHEN_OPERATION_IS_CREATE() throws Exception {
        StoryBibleApplicationService storyBibleApplicationService = mock(StoryBibleApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskId(8001L);
        task.setProjectId(9001L);
        task.setUserId(1001L);
        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);

        StoryBibleEntry created = new StoryBibleEntry();
        created.setEntryId(88002L);
        created.setProjectId(9001L);
        created.setEntryKey("maid.secret_order");
        created.setEntryType("character");
        created.setTitle("侍从密令");
        created.setContent("侍从负责转述密令。");
        created.setCanonicalStatus("PROPOSED");
        created.setRiskLevel(2);
        when(storyBibleApplicationService.createEntry(
                org.mockito.ArgumentMatchers.eq(9001L),
                org.mockito.ArgumentMatchers.any(StoryBibleEntry.class),
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq("trace-story-bible-service")
        )).thenReturn(created);

        Object service = instantiate(
                "com.penmate.backend.application.agent.tool.DefaultStoryBibleUpdateApplicationService",
                Map.of(
                        StoryBibleApplicationService.class, storyBibleApplicationService,
                        AgentRepository.class, agentRepository
                )
        );

        ToolCallResult result = execute(service, request("""
                {
                  "operation": "create",
                  "entryKey": "maid.secret_order",
                  "entryType": "character",
                  "title": "侍从密令",
                  "content": "侍从负责转述密令。",
                  "canonicalStatus": "PROPOSED",
                  "riskLevel": 2,
                  "chapterId": 301
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput())
                .contains("maid.secret_order")
                .contains("侍从密令")
                .contains("PROPOSED")
                .contains("88002");
    }

    @Test
    void UT_APP_AGENT_STORY_BIBLE_UPDATE_APPLICATION_SERVICE_EXECUTE_SHOULD_UPDATE_ENTRY_VIA_APPLICATION_SERVICE() throws Exception {
        StoryBibleApplicationService storyBibleApplicationService = mock(StoryBibleApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskId(8001L);
        task.setProjectId(9001L);
        task.setUserId(1001L);
        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);

        StoryBibleEntry updated = new StoryBibleEntry();
        updated.setEntryId(88001L);
        updated.setProjectId(9001L);
        updated.setEntryKey("maid.secret_order");
        updated.setEntryType("character");
        updated.setTitle("侍从密令");
        updated.setContent("侍从负责转述密令，并知晓部分内情。");
        updated.setCanonicalStatus("PROPOSED");
        updated.setRiskLevel(3);
        when(storyBibleApplicationService.updateEntry(
                org.mockito.ArgumentMatchers.eq(9001L),
                org.mockito.ArgumentMatchers.eq(88001L),
                org.mockito.ArgumentMatchers.any(StoryBibleEntry.class),
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq("trace-story-bible-service")
        )).thenReturn(updated);

        Object service = instantiate(
                "com.penmate.backend.application.agent.tool.DefaultStoryBibleUpdateApplicationService",
                Map.of(
                        StoryBibleApplicationService.class, storyBibleApplicationService,
                        AgentRepository.class, agentRepository
                )
        );

        ToolCallResult result = execute(service, request("""
                {
                  "operation": "update",
                  "entryId": 88001,
                  "entryKey": "maid.secret_order",
                  "entryType": "character",
                  "title": "侍从密令",
                  "content": "侍从负责转述密令，并知晓部分内情。",
                  "canonicalStatus": "PROPOSED",
                  "riskLevel": 3,
                  "chapterId": 301
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput())
                .contains("maid.secret_order")
                .contains("侍从负责转述密令，并知晓部分内情。")
                .contains("PROPOSED");
    }

    @Test
    void UT_APP_AGENT_STORY_BIBLE_UPDATE_APPLICATION_SERVICE_EXECUTE_SHOULD_DELETE_ENTRY_WITH_STRUCTURED_RESULT() throws Exception {
        StoryBibleApplicationService storyBibleApplicationService = mock(StoryBibleApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskId(8001L);
        task.setProjectId(9001L);
        task.setUserId(1001L);
        when(agentRepository.findGenerationTask(9001L, 8001L)).thenReturn(task);

        Object service = instantiate(
                "com.penmate.backend.application.agent.tool.DefaultStoryBibleUpdateApplicationService",
                Map.of(
                        StoryBibleApplicationService.class, storyBibleApplicationService,
                        AgentRepository.class, agentRepository
                )
        );

        ToolCallResult result = execute(service, request("""
                {
                  "operation": "delete",
                  "entryId": 88001
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput())
                .contains("\"operation\":\"delete\"")
                .contains("\"entryId\":88001")
                .contains("\"deleted\":true");
    }

    @Test
    void UT_APP_AGENT_STORY_BIBLE_UPDATE_APPLICATION_SERVICE_EXECUTE_SHOULD_REJECT_WHEN_TASK_CONTEXT_IS_MISSING() throws Exception {
        StoryBibleApplicationService storyBibleApplicationService = mock(StoryBibleApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        Object service = instantiate(
                "com.penmate.backend.application.agent.tool.DefaultStoryBibleUpdateApplicationService",
                Map.of(
                        StoryBibleApplicationService.class, storyBibleApplicationService,
                        AgentRepository.class, agentRepository
                )
        );
        ToolCallRequest missingTaskRequest = new ToolCallRequest(
                9001L,
                null,
                6001L,
                "story_bible_update",
                "{\"operation\":\"list\",\"chapterId\":301}",
                1001L,
                "trace-story-bible-missing-task",
                "{}",
                "idem-story-bible-missing-task"
        );

        try {
            execute(service, missingTaskRequest);
        } catch (Exception ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            assertThat(String.valueOf(cause.getMessage())).contains("task").contains("required");
            return;
        }
        throw new AssertionError("Expected missing task context to be rejected");
    }

    private static Object instantiate(String fqcn, Map<Class<?>, Object> provided) throws Exception {
        Class<?> type = loadClass(fqcn);
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        java.util.Arrays.sort(constructors, (left, right) -> Integer.compare(right.getParameterCount(), left.getParameterCount()));
        for (Constructor<?> constructor : constructors) {
            Object[] args = resolveArguments(constructor.getParameterTypes(), provided);
            if (args == null) {
                continue;
            }
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        throw new AssertionError("No satisfiable constructor found for " + fqcn);
    }

    private static Object[] resolveArguments(Class<?>[] parameterTypes, Map<Class<?>, Object> provided) {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object candidate = findProvided(parameterTypes[i], provided);
            if (candidate == null) {
                return null;
            }
            args[i] = candidate;
        }
        return args;
    }

    private static Object findProvided(Class<?> parameterType, Map<Class<?>, Object> provided) {
        for (Map.Entry<Class<?>, Object> entry : provided.entrySet()) {
            if (parameterType.isAssignableFrom(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Class<?> loadClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected class to exist: " + fqcn, ex);
        }
    }

    private static ToolCallResult execute(Object service, ToolCallRequest request) throws Exception {
        Method method = service.getClass().getMethod("execute", ToolCallRequest.class);
        method.setAccessible(true);
        return (ToolCallResult) method.invoke(service, request);
    }

    private static ToolCallRequest request(String toolArgsJson) {
        return new ToolCallRequest(
                9001L,
                8001L,
                6001L,
                "story_bible_update",
                toolArgsJson,
                1001L,
                "trace-story-bible-service",
                "{}",
                "idem-story-bible-service"
        );
    }
}
