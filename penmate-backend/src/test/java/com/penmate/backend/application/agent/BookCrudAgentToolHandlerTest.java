package com.penmate.backend.application.agent;

import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateProjectCommand;
import com.penmate.backend.domain.novel.model.NovelProject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookCrudAgentToolHandlerTest {

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_CREATES_BOOK_VIA_NOVEL_APPLICATION_SERVICE() {
        NovelApplicationService novelApplicationService = mock(NovelApplicationService.class);
        NovelProject created = new NovelProject();
        created.setProjectId(990001L);
        created.setTitle("银河笔记");
        created.setSummary("科幻冒险");
        created.setStatus(1);
        when(novelApplicationService.createProject(any(CreateProjectCommand.class), eq("trace-book-create"))).thenReturn(created);

        Object handler = instantiateHandler(novelApplicationService);
        ToolInvocationRequest request = new ToolInvocationRequest(
                0L,
                8008L,
                7001L,
                "book_crud",
                "{\"operation\":\"create\",\"ownerUserId\":1001,\"title\":\"银河笔记\",\"summary\":\"科幻冒险\",\"status\":1}",
                1001L,
                "trace-book-create",
                "{}",
                "book-crud-create-galaxy-note"
        );

        Object result = execute(handler, request);

        assertThat(invokeAccessor(result, "status")).isEqualTo("SUCCESS");
        assertThat((String) invokeAccessor(result, "toolOutput")).contains("\"projectId\":990001");
        assertThat((String) invokeAccessor(result, "toolOutput")).contains("银河笔记");

        ArgumentCaptor<CreateProjectCommand> commandCaptor = ArgumentCaptor.forClass(CreateProjectCommand.class);
        verify(novelApplicationService).createProject(commandCaptor.capture(), eq("trace-book-create"));
        assertThat(commandCaptor.getValue().ownerUserId()).isEqualTo(1001L);
        assertThat(commandCaptor.getValue().title()).isEqualTo("银河笔记");
        assertThat(commandCaptor.getValue().summary()).isEqualTo("科幻冒险");
        assertThat(commandCaptor.getValue().status()).isEqualTo(1);
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_LISTS_BOOKS_VIA_NOVEL_APPLICATION_SERVICE() {
        NovelApplicationService novelApplicationService = mock(NovelApplicationService.class);
        NovelProject first = new NovelProject();
        first.setProjectId(990101L);
        first.setTitle("银河笔记");
        first.setSummary("科幻冒险");
        first.setStatus(1);
        NovelProject second = new NovelProject();
        second.setProjectId(990102L);
        second.setTitle("雾港手记");
        second.setSummary("悬疑推理");
        second.setStatus(2);
        when(novelApplicationService.listProjects()).thenReturn(List.of(first, second));

        Object handler = instantiateHandler(novelApplicationService);
        ToolInvocationRequest request = new ToolInvocationRequest(
                0L,
                8009L,
                7001L,
                "book_crud",
                "{\"operation\":\"list\"}",
                1001L,
                "trace-book-list",
                "{}",
                "book-crud-list"
        );

        Object result = execute(handler, request);

        assertThat(invokeAccessor(result, "status")).isEqualTo("SUCCESS");
        assertThat((String) invokeAccessor(result, "toolOutput")).contains("银河笔记");
        assertThat((String) invokeAccessor(result, "toolOutput")).contains("雾港手记");
        verify(novelApplicationService).listProjects();
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_UPDATES_BOOK_VIA_NOVEL_APPLICATION_SERVICE() {
        NovelApplicationService novelApplicationService = mock(NovelApplicationService.class);
        NovelProject updated = new NovelProject();
        updated.setProjectId(990201L);
        updated.setTitle("银河笔记·修订版");
        updated.setSummary("科幻冒险升级");
        updated.setStatus(2);
        when(novelApplicationService.updateProject(eq(990201L), any(UpdateProjectCommand.class), eq("trace-book-update"))).thenReturn(updated);

        Object handler = instantiateHandler(novelApplicationService);
        ToolInvocationRequest request = new ToolInvocationRequest(
                0L,
                8011L,
                7001L,
                "book_crud",
                "{\"operation\":\"update\",\"projectId\":990201,\"title\":\"银河笔记·修订版\",\"summary\":\"科幻冒险升级\",\"status\":2}",
                1001L,
                "trace-book-update",
                "{}",
                "book-crud-update-galaxy-note"
        );

        Object result = execute(handler, request);

        assertThat(invokeAccessor(result, "status")).isEqualTo("SUCCESS");
        assertThat((String) invokeAccessor(result, "toolOutput")).contains("银河笔记·修订版");

        ArgumentCaptor<UpdateProjectCommand> commandCaptor = ArgumentCaptor.forClass(UpdateProjectCommand.class);
        verify(novelApplicationService).updateProject(eq(990201L), commandCaptor.capture(), eq("trace-book-update"));
        assertThat(commandCaptor.getValue().title()).isEqualTo("银河笔记·修订版");
        assertThat(commandCaptor.getValue().summary()).isEqualTo("科幻冒险升级");
        assertThat(commandCaptor.getValue().status()).isEqualTo(2);
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_DELETES_BOOK_VIA_NOVEL_APPLICATION_SERVICE() {
        NovelApplicationService novelApplicationService = mock(NovelApplicationService.class);

        Object handler = instantiateHandler(novelApplicationService);
        ToolInvocationRequest request = new ToolInvocationRequest(
                0L,
                8012L,
                7001L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":990202}",
                1001L,
                "trace-book-delete",
                "{}",
                "book-crud-delete-990202"
        );

        Object result = execute(handler, request);

        assertThat(invokeAccessor(result, "status")).isEqualTo("SUCCESS");
        assertThat((String) invokeAccessor(result, "toolOutput")).contains("deleted");
        verify(novelApplicationService).deleteProject(990202L, 1001L, "trace-book-delete");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_DELETE_REQUIRES_PROJECT_ID() {
        NovelApplicationService novelApplicationService = mock(NovelApplicationService.class);

        Object handler = instantiateHandler(novelApplicationService);
        ToolInvocationRequest request = new ToolInvocationRequest(
                0L,
                8015L,
                7001L,
                "book_crud",
                "{\"operation\":\"delete\",\"bookId\":990202}",
                1001L,
                "trace-book-delete-invalid",
                "{}",
                "book-crud-delete-invalid"
        );

        assertThatThrownBy(() -> executeRaw(handler, request))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("projectId is required");
    }

    private Object instantiateHandler(NovelApplicationService novelApplicationService) {
        try {
            Class<?> type = Class.forName("com.penmate.backend.application.agent.BookCrudAgentToolHandler");
            Constructor<?> constructor = type.getDeclaredConstructor(NovelApplicationService.class);
            constructor.setAccessible(true);
            return constructor.newInstance(novelApplicationService);
        } catch (Exception ex) {
            throw new AssertionError("expected book crud handler to be constructible", ex);
        }
    }

    private Object execute(Object handler, ToolInvocationRequest request) {
        try {
            Method method = handler.getClass().getMethod("execute", ToolInvocationRequest.class);
            return method.invoke(handler, request);
        } catch (Exception ex) {
            throw new AssertionError("expected book crud handler execution to succeed", ex);
        }
    }

    private Object executeRaw(Object handler, ToolInvocationRequest request) throws Exception {
        Method method = handler.getClass().getMethod("execute", ToolInvocationRequest.class);
        return method.invoke(handler, request);
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
