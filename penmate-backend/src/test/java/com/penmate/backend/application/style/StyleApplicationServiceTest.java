package com.penmate.backend.application.style;

import com.penmate.backend.application.style.command.StyleCommands.AnalyzeStyleCommand;
import com.penmate.backend.application.style.command.StyleCommands.CreateStyleCommand;
import com.penmate.backend.application.style.command.StyleCommands.SwitchStyleCommand;
import com.penmate.backend.application.style.command.StyleCommands.UpdateStyleCommand;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import com.penmate.backend.domain.style.model.StyleProfile;
import com.penmate.backend.domain.style.repository.StyleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StyleApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private StyleRepository styleRepository;

    @Mock
    private RealtimeEventService realtimeEventService;

    @InjectMocks
    private StyleApplicationService styleApplicationService;

    @Test
    void UT_APP_STYLE_LIST_STYLES_SUCCESS() {
        Long projectId = 1L;
        StyleProfile s1 = new StyleProfile();
        StyleProfile s2 = new StyleProfile();
        when(styleRepository.findByProjectId(projectId)).thenReturn(List.of(s1, s2));

        List<StyleProfile> result = styleApplicationService.listStyles(projectId);

        assertThat(result).hasSize(2);
        verify(styleRepository).findByProjectId(projectId);
        verifyNoInteractions(auditService, realtimeEventService);
    }

    @Test
    void UT_APP_STYLE_GET_STYLE_NOT_FOUND() {
        Long projectId = 1L;
        Long styleId = 9999L;
        when(styleRepository.findById(projectId, styleId)).thenReturn(null);

        assertThatThrownBy(() -> styleApplicationService.getStyle(projectId, styleId))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Style not found");
    }

    @Test
    void UT_APP_STYLE_CREATE_STYLE_SUCCESS() {
        Long projectId = 1L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-STYLE-CREATE";

        when(styleRepository.insert(any(StyleProfile.class))).thenAnswer(invocation -> {
            StyleProfile style = invocation.getArgument(0);
            style.setId(11L);
            return 1;
        });

        StyleProfile result = styleApplicationService.createStyle(
                projectId,
                new CreateStyleCommand("古风", true, "慢", "严肃", "人物", "模板", "示例", operatorId),
                traceId
        );

        assertThat(result.getId()).isEqualTo(11L);
        assertThat(result.getName()).isEqualTo("古风");
        verify(styleRepository).clearDefaultByProjectId(projectId);
        verify(styleRepository).insert(any(StyleProfile.class));
        verifyNoInteractions(realtimeEventService);
    }

    @Test
    void UT_APP_STYLE_UPDATE_STYLE_SUCCESS() {
        Long projectId = 1L;
        Long styleId = 2L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-STYLE-UPDATE";

        StyleProfile existing = new StyleProfile();
        existing.setId(styleId);
        existing.setName("旧风格");
        when(styleRepository.findById(projectId, styleId)).thenReturn(existing);
        when(styleRepository.update(any(StyleProfile.class))).thenReturn(1);

        StyleProfile result = styleApplicationService.updateStyle(
                projectId,
                styleId,
                new UpdateStyleCommand("新风格", "快", "轻松", "情节", "新模板", "新示例", operatorId),
                traceId
        );

        assertThat(result.getName()).isEqualTo("新风格");
        verify(styleRepository, times(2)).findById(projectId, styleId);
        verify(styleRepository).update(any(StyleProfile.class));
        verifyNoInteractions(realtimeEventService);
    }

    @Test
    void UT_APP_STYLE_DELETE_STYLE_NOT_FOUND() {
        Long projectId = 1L;
        Long styleId = 9999L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-STYLE-DELETE-FAIL";
        when(styleRepository.softDelete(projectId, styleId)).thenReturn(0);

        assertThatThrownBy(() -> styleApplicationService.deleteStyle(projectId, styleId, operatorId, traceId))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Style not found");
    }

    @Test
    void UT_APP_STYLE_SWITCH_STYLE_SUCCESS() {
        Long projectId = 1L;
        Long toStyleId = 9L;
        Long operatorId = 1001L;
        String traceId = "UT-TRACE-STYLE-SWITCH";

        StyleProfile toStyle = new StyleProfile();
        toStyle.setId(toStyleId);
        StyleProfile fromStyle = new StyleProfile();
        fromStyle.setId(3L);

        when(styleRepository.findById(projectId, toStyleId)).thenReturn(toStyle);
        when(styleRepository.findDefaultByProjectId(projectId)).thenReturn(fromStyle);
        when(styleRepository.setDefault(projectId, toStyleId)).thenReturn(1);

        StyleProfile result = styleApplicationService.switchStyle(
                projectId,
                new SwitchStyleCommand(toStyleId, true, "切换文风", operatorId),
                traceId
        );

        assertThat(result.getId()).isEqualTo(toStyleId);
        verify(styleRepository).clearDefaultByProjectId(projectId);
        verify(styleRepository).setDefault(projectId, toStyleId);
        verify(styleRepository).insertSwitchLog(projectId, 3L, toStyleId, operatorId, true, "切换文风");
        verify(realtimeEventService).publishProjectEvent(eq(projectId), eq("style.switched"), anyMap());
    }

    @Test
    void UT_APP_STYLE_SWITCH_STYLE_SET_DEFAULT_FAIL() {
        Long projectId = 1L;
        Long toStyleId = 9L;

        StyleProfile toStyle = new StyleProfile();
        toStyle.setId(toStyleId);
        when(styleRepository.findById(projectId, toStyleId)).thenReturn(toStyle);
        when(styleRepository.setDefault(projectId, toStyleId)).thenReturn(0);

        assertThatThrownBy(() -> styleApplicationService.switchStyle(
                projectId,
                new SwitchStyleCommand(toStyleId, false, "切换", 1001L),
                "trace"
        )).isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Failed to switch default style");
    }

    @Test
    void UT_APP_STYLE_ANALYZE_SAMPLE_SUCCESS() {
        Long projectId = 1L;
        Long operatorId = 1001L;
        String sampleText = "这是一段示例文本";
        String traceId = "UT-TRACE-STYLE-ANALYZE";

        Map<String, Object> result = styleApplicationService.analyzeSample(
                projectId,
                new AnalyzeStyleCommand(sampleText, operatorId),
                traceId
        );

        assertThat(result).containsKeys("projectId", "suggestedTone", "suggestedPace", "keywords", "sampleLength");
        verifyNoInteractions(realtimeEventService);
    }
}

