package com.penmate.backend.application.novel;

import com.penmate.backend.application.support.BaseApplicationServiceTest;
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
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project not found");
    }
}

