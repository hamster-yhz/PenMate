package com.penmate.backend.interfaces.api.novel;

import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.application.novel.NovelCoverApplicationService;
import com.penmate.backend.application.novel.NovelTrashApplicationService;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.interfaces.api.novel.dto.PermanentlyDeleteNovelProjectDto;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NovelTrashControllerTest {

    private final NovelApplicationService novels = mock(NovelApplicationService.class);
    private final NovelTrashApplicationService trash = mock(NovelTrashApplicationService.class);
    private final NovelCoverApplicationService covers = mock(NovelCoverApplicationService.class);
    private final NovelController controller = new NovelController(novels, trash, covers);
    private final Authentication actor = UsernamePasswordAuthenticationToken.authenticated("1001", "n/a", List.of());

    @Test
    void delegates_trash_queries_and_commands_with_the_authenticated_actor() {
        when(trash.listDeletedProjects(1001L)).thenReturn(List.of());
        NovelProject restored = new NovelProject();
        when(trash.restoreProject(2001L, 1001L)).thenReturn(restored);
        PermanentlyDeleteNovelProjectDto confirmation = new PermanentlyDeleteNovelProjectDto();
        confirmation.setConfirmationTitle("北境来信");

        controller.listDeletedProjects(actor, "trace-list");
        controller.restoreDeletedProject("2001", actor, "trace-restore");
        controller.permanentlyDeleteProject("2001", confirmation, actor, "trace-delete");

        verify(trash).listDeletedProjects(1001L);
        verify(trash).restoreProject(2001L, 1001L);
        verify(trash).permanentlyDeleteProject(2001L, 1001L, "北境来信");
    }
}
