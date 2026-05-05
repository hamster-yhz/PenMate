package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.novel.NovelApplicationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookCrudToolHandlerTest {

    private final NovelApplicationService novelApplicationService = mock(NovelApplicationService.class);
    private final BookCrudToolHandler handler = new BookCrudToolHandler(novelApplicationService);

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_WHEN_OPERATION_MISSING() {
        ToolCallRequest request = request("{}");

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operation is required");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_UNSUPPORTED_OPERATION() {
        ToolCallRequest request = request("""
                {
                  "operation": "archive"
                }
                """);

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported operation: archive");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_CREATE_WHEN_OWNER_USER_ID_MISSING() {
        ToolCallRequest request = request("""
                {
                  "operation": "create",
                  "title": "三体"
                }
                """);

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ownerUserId is required");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_CREATE_WHEN_TITLE_BLANK() {
        ToolCallRequest request = request("""
                {
                  "operation": "create",
                  "ownerUserId": 1001,
                  "title": "   "
                }
                """);

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title is required");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_UPDATE_WHEN_PROJECT_ID_MISSING() {
        ToolCallRequest request = request("""
                {
                  "operation": "update",
                  "title": "新标题"
                }
                """);

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("projectId is required");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_DELETE_WHEN_PROJECT_ID_MISSING() {
        ToolCallRequest request = request("""
                {
                  "operation": "delete"
                }
                """);

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("projectId is required");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_ACCEPT_MINIMAL_LIST_REQUEST() {
        ToolCallRequest request = request("""
                {
                  "operation": "list"
                }
                """);

        handler.validate(request);
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_LIST_WITH_PROJECT_ID() {
        ToolCallRequest request = request("""
                {
                  "operation": "list",
                  "projectId": 9001
                }
                """);

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unexpected field for operation list: projectId");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_DELETE_WITH_TITLE() {
        ToolCallRequest request = request("""
                {
                  "operation": "delete",
                  "projectId": 9001,
                  "title": "不应出现"
                }
                """);

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unexpected field for operation delete: title");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_UPDATE_WITH_OWNER_USER_ID() {
        ToolCallRequest request = request("""
                {
                  "operation": "update",
                  "projectId": 9001,
                  "ownerUserId": 1001
                }
                """);

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unexpected field for operation update: ownerUserId");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_INVALID_JSON() {
        ToolCallRequest request = request("{");

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid tool args");
    }

    @Test
    void UT_APP_AGENT_BOOK_CRUD_TOOL_HANDLER_EXECUTE_SHOULD_MAP_UNEXPECTED_EXCEPTION_TO_FAILED_RESULT() {
        ToolCallRequest request = request("""
                {
                  "operation": "list"
                }
                """);
        when(novelApplicationService.listProjects()).thenThrow(new RuntimeException("boom"));

        ToolCallResult result = handler.execute(request);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("BOOK_CRUD_EXECUTION_FAILED");
        assertThat(result.errorMessage()).isEqualTo("boom");
    }

    private ToolCallRequest request(String toolArgsJson) {
        return new ToolCallRequest(
                9001L,
                8001L,
                7001L,
                "book_crud",
                toolArgsJson,
                1001L,
                "trace-1",
                "{}",
                "idem-1"
        );
    }
}
