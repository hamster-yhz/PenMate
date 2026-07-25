package com.penmate.backend.application.agent.tool.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.tool.definition.BookCrudToolDefinition;
import com.penmate.backend.application.agent.tool.definition.ToolLifecycleStatus;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import static com.penmate.backend.application.agent.tool.runtime.AgentToolTestContext.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookCrudToolHandlerTest {

    private final NovelApplicationService novels = mock(NovelApplicationService.class);
    private final BookCrudToolHandler handler = new BookCrudToolHandler(
            novels, new JacksonJsonCodec(new ObjectMapper()));

    @Test
    void is_disabled_and_does_not_expose_identity_parameters() {
        var descriptor = new BookCrudToolDefinition().descriptor();
        assertThat(descriptor.exposure().lifecycleStatus()).isEqualTo(ToolLifecycleStatus.DISABLED);
        assertThat(descriptor.exposure().parametersJsonSchema())
                .doesNotContain("ownerUserId", "projectId", "sessionId", "runId");
    }

    @Test
    void reads_only_the_project_from_authorized_run_context() {
        NovelProject project = project();
        when(novels.getProject(9001L)).thenReturn(project);

        var result = handler.execute(context(), request("{\"operation\":\"get\"}"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput()).contains("\"projectId\":\"9001\"");
        verify(novels).getProject(9001L);
    }

    @Test
    void rejects_model_supplied_project_or_owner_identity() {
        assertThatThrownBy(() -> handler.validate(context(), request(
                "{\"operation\":\"update\",\"projectId\":42}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected field");
        assertThatThrownBy(() -> handler.validate(context(), request(
                "{\"operation\":\"update\",\"ownerUserId\":42}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected field");
    }

    private ToolCallRequest request(String arguments) {
        return new ToolCallRequest(8001L, "book_crud", arguments, "idem-1", "call-1", 1L);
    }

    private NovelProject project() {
        NovelProject project = new NovelProject();
        project.setProjectId(9001L);
        project.setOwnerUserId(1001L);
        project.setTitle("Current project");
        project.setStatus(1);
        return project;
    }
}
