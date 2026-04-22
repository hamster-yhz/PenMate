package com.penmate.backend.application.plugin;

import com.penmate.backend.application.plugin.command.PluginCommands.*;
import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginCatalogItem;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import com.penmate.backend.domain.plugin.repository.PluginRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private PluginRepository pluginRepository;

    @InjectMocks
    private PluginApplicationService pluginApplicationService;

    @Test
    void UT_APP_PLUGIN_LIST_CATALOG_SUCCESS() {
        // given
        PluginCatalogItem item1 = new PluginCatalogItem();
        PluginCatalogItem item2 = new PluginCatalogItem();
        when(pluginRepository.listCatalog()).thenReturn(Arrays.asList(item1, item2));

        // when
        List<PluginCatalogItem> result = pluginApplicationService.listCatalog();

        // then
        verify(pluginRepository).listCatalog();
        verifyNoInteractions(auditService);
        assert result.size() == 2;
    }

    @Test
    void UT_APP_PLUGIN_GET_CATALOG_SUCCESS() {
        // given
        String pluginCode = "search-tool";
        PluginCatalogItem item = new PluginCatalogItem();
        item.setCode(pluginCode);
        when(pluginRepository.getCatalogByCode(pluginCode)).thenReturn(item);

        // when
        PluginCatalogItem result = pluginApplicationService.getCatalog(pluginCode);

        // then
        verify(pluginRepository).getCatalogByCode(pluginCode);
        verifyNoInteractions(auditService);
        assert result.getCode().equals(pluginCode);
    }

    @Test
    void UT_APP_PLUGIN_GET_CATALOG_NOT_FOUND() {
        // given
        String pluginCode = "not-found";
        when(pluginRepository.getCatalogByCode(pluginCode)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> pluginApplicationService.getCatalog(pluginCode))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Plugin not found");
    }

    @Test
    void UT_APP_PLUGIN_LIST_PROJECT_INSTALLS_SUCCESS() {
        // given
        Long projectId = 1L;
        PluginProjectInstall install1 = new PluginProjectInstall();
        PluginProjectInstall install2 = new PluginProjectInstall();
        when(pluginRepository.listProjectInstalls(projectId)).thenReturn(Arrays.asList(install1, install2));

        // when
        List<PluginProjectInstall> result = pluginApplicationService.listProjectInstalls(projectId);

        // then
        verify(pluginRepository).listProjectInstalls(projectId);
        verifyNoInteractions(auditService);
        assert result.size() == 2;
    }

    @Test
    void UT_APP_PLUGIN_INSTALL_SUCCESS() {
        // given
        Long projectId = 1L;
        Long operatorId = 1001L;
        String pluginCode = "search-tool";
        String version = "1.0.0";
        String configJson = "{\"apiKey\":\"xxx\"}";
        String traceId = "UT-TRACE-PLUGIN-INSTALL";
        when(pluginRepository.findCatalogIdByCode(pluginCode)).thenReturn(1L);
        when(pluginRepository.insertInstall(eq(projectId), eq(1L), eq(version), eq(configJson), eq(true), eq(operatorId))).thenReturn(1);

        // when
        pluginApplicationService.install(
                projectId,
                new InstallPluginCommand(pluginCode, version, configJson, operatorId),
                traceId
        );

        // then
        verify(pluginRepository).findCatalogIdByCode(pluginCode);
        verify(pluginRepository).insertInstall(eq(projectId), eq(1L), eq(version), eq(configJson), eq(true), eq(operatorId));
    }

    @Test
    void UT_APP_PLUGIN_INSTALL_PLUGIN_NOT_FOUND() {
        // given
        Long projectId = 1L;
        Long operatorId = 1001L;
        String pluginCode = "not-found";
        String version = "1.0.0";
        String configJson = "{\"apiKey\":\"xxx\"}";
        String traceId = "UT-TRACE-PLUGIN-INSTALL-FAIL";
        when(pluginRepository.findCatalogIdByCode(pluginCode)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> pluginApplicationService.install(
                projectId,
                new InstallPluginCommand(pluginCode, version, configJson, operatorId),
                traceId
        )).isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Plugin not found");
    }

    @Test
    void UT_APP_PLUGIN_INSTALL_FAILED() {
        // given
        Long projectId = 1L;
        Long operatorId = 1001L;
        String pluginCode = "search-tool";
        String version = "1.0.0";
        String configJson = "{\"apiKey\":\"xxx\"}";
        String traceId = "UT-TRACE-PLUGIN-INSTALL-FAIL";
        when(pluginRepository.findCatalogIdByCode(pluginCode)).thenReturn(1L);
        when(pluginRepository.insertInstall(eq(projectId), eq(1L), eq(version), eq(configJson), eq(true), eq(operatorId))).thenReturn(0);

        // when & then
        assertThatThrownBy(() -> pluginApplicationService.install(
                projectId,
                new InstallPluginCommand(pluginCode, version, configJson, operatorId),
                traceId
        )).isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Failed to install plugin");
    }

    @Test
    void UT_APP_PLUGIN_UPDATE_INSTALL_SUCCESS() {
        // given
        Long projectId = 1L;
        Long operatorId = 1001L;
        String pluginCode = "search-tool";
        Boolean enabled = false;
        String configJson = "{\"apiKey\":\"yyy\"}";
        String traceId = "UT-TRACE-PLUGIN-UPDATE";
        when(pluginRepository.updateInstall(projectId, pluginCode, enabled, configJson)).thenReturn(1);

        // when
        pluginApplicationService.updateInstall(
                projectId,
                pluginCode,
                new UpdatePluginInstallCommand(enabled, configJson, operatorId),
                traceId
        );

        // then
        verify(pluginRepository).updateInstall(projectId, pluginCode, enabled, configJson);
    }

    @Test
    void UT_APP_PLUGIN_UPDATE_INSTALL_NOT_FOUND() {
        // given
        Long projectId = 1L;
        Long operatorId = 1001L;
        String pluginCode = "search-tool";
        Boolean enabled = false;
        String configJson = "{\"apiKey\":\"yyy\"}";
        String traceId = "UT-TRACE-PLUGIN-UPDATE-FAIL";
        when(pluginRepository.updateInstall(projectId, pluginCode, enabled, configJson)).thenReturn(0);

        // when & then
        assertThatThrownBy(() -> pluginApplicationService.updateInstall(
                projectId,
                pluginCode,
                new UpdatePluginInstallCommand(enabled, configJson, operatorId),
                traceId
        )).isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Plugin install not found");
    }

    @Test
    void UT_APP_PLUGIN_DELETE_INSTALL_SUCCESS() {
        // given
        Long projectId = 1L;
        Long operatorId = 1001L;
        String pluginCode = "search-tool";
        String traceId = "UT-TRACE-PLUGIN-DELETE";
        when(pluginRepository.deleteInstall(projectId, pluginCode)).thenReturn(1);

        // when
        pluginApplicationService.deleteInstall(projectId, pluginCode, operatorId, traceId);

        // then
        verify(pluginRepository).deleteInstall(projectId, pluginCode);
    }

    @Test
    void UT_APP_PLUGIN_DELETE_INSTALL_NOT_FOUND() {
        // given
        Long projectId = 1L;
        Long operatorId = 1001L;
        String pluginCode = "search-tool";
        String traceId = "UT-TRACE-PLUGIN-DELETE-FAIL";
        when(pluginRepository.deleteInstall(projectId, pluginCode)).thenReturn(0);

        // when & then
        assertThatThrownBy(() -> pluginApplicationService.deleteInstall(projectId, pluginCode, operatorId, traceId))
                .isExactlyInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .hasMessage("Plugin install not found");
    }

    @Test
    void UT_APP_PLUGIN_LIST_CALL_LOGS_SUCCESS() {
        // given
        Long projectId = 1L;
        PluginCallLog log1 = new PluginCallLog();
        PluginCallLog log2 = new PluginCallLog();
        when(pluginRepository.listCallLogs(projectId)).thenReturn(Arrays.asList(log1, log2));

        // when
        List<PluginCallLog> result = pluginApplicationService.listCallLogs(projectId);

        // then
        verify(pluginRepository).listCallLogs(projectId);
        verifyNoInteractions(auditService);
        assert result.size() == 2;
    }

    @Test
    void UT_APP_PLUGIN_RECORD_TOOL_CALL_SUCCESS() {
        PluginCallLog callLog = new PluginCallLog();
        callLog.setProjectId(1L);
        callLog.setTaskId(2L);
        callLog.setPluginCode("search-tool");
        callLog.setToolName("context_enhancer");
        when(pluginRepository.insertCallLog(callLog)).thenReturn(1);

        pluginApplicationService.recordToolCall(callLog);

        verify(pluginRepository).insertCallLog(callLog);
        verifyNoInteractions(auditService);
    }
}

