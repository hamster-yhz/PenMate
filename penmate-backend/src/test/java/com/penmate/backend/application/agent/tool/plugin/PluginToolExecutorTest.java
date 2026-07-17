package com.penmate.backend.application.agent.tool.plugin;

import com.penmate.backend.application.plugin.PluginApplicationService;
import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginToolExecutorTest {

    @Mock
    private PluginApplicationService pluginApplicationService;

    @Test
    void recordsRunIdentityInPluginRequestPayload() {
        PluginProjectInstall install = new PluginProjectInstall();
        install.setPluginCode("context-pack");
        install.setEnabled(true);
        when(pluginApplicationService.listProjectInstalls(1001L)).thenReturn(List.of(install));
        PluginToolExecutor executor = new PluginToolExecutor(pluginApplicationService);

        executor.execute(new PluginToolExecuteCommand(1001L, 9001L, "tighten the scene", "trace-plugin"));

        ArgumentCaptor<PluginCallLog> captor = ArgumentCaptor.forClass(PluginCallLog.class);
        verify(pluginApplicationService).recordToolCall(captor.capture());
        PluginCallLog log = captor.getValue();
        assertThat(log.getRequestJson()).contains("\"runId\":9001");
        assertThat(log.getRequestJson()).doesNotContain("taskId");
    }
}
