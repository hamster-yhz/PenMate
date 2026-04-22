package com.penmate.backend.application.novel;

import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.application.novel.command.NovelCommands.CreateOutlineNodeCommand;
import com.penmate.backend.domain.novel.model.NovelOutlineNode;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private NovelGateway novelGateway;

    @Mock
    private RealtimeEventService realtimeEventService;

    @InjectMocks
    private NovelApplicationService novelApplicationService;

    @Test
    void UT_APP_NOVEL_LIST_PROJECTS_SUCCESS() {
        when(novelGateway.findAllProjects()).thenReturn(List.of(new NovelProject(), new NovelProject()));
        assertThat(novelApplicationService.listProjects()).hasSize(2);
        verify(novelGateway).findAllProjects();
    }

    @Test
    void UT_APP_NOVEL_GET_PROJECT_NOT_FOUND() {
        when(novelGateway.findProjectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> novelApplicationService.getProject(1L))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Project not found");
    }

    @Test
    void UT_APP_NOVEL_CREATE_OUTLINE_NODE_ALLOW_NULL_PARENT_ID() {
        NovelProject project = new NovelProject();
        project.setId(920002L);
        project.setTitle("DBCASE_长夜行_连载");
        when(novelGateway.findProjectById(920002L)).thenReturn(project);
        when(novelGateway.insertOutlineNode(any(NovelOutlineNode.class))).thenAnswer(invocation -> {
            NovelOutlineNode node = invocation.getArgument(0);
            node.setId(123L);
            return 1;
        });
        doNothing().when(realtimeEventService).publishProjectEvent(eq(920002L), eq("outline.node.created"), any());

        NovelOutlineNode created = novelApplicationService.createOutlineNode(
                920002L,
                new CreateOutlineNodeCommand(null, "第一卷：新的篇章", "chapter", 1, "content"),
                920001L,
                "trace-test"
        );

        assertThat(created.getId()).isEqualTo(123L);
        assertThat(created.getParentId()).isNull();
        verify(realtimeEventService).publishProjectEvent(eq(920002L), eq("outline.node.created"), any());
    }
}


