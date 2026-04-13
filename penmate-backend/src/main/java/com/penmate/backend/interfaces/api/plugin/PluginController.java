package com.penmate.backend.interfaces.api.plugin;

import com.penmate.backend.application.plugin.PluginApplicationService;
import com.penmate.backend.application.plugin.command.PluginCommands;
import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginCatalogItem;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.plugin.dto.InstallPluginDto;
import com.penmate.backend.interfaces.api.plugin.dto.UpdatePluginInstallDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PluginController {

    private final PluginApplicationService pluginApplicationService;

    public PluginController(PluginApplicationService pluginApplicationService) {
        this.pluginApplicationService = pluginApplicationService;
    }

    @GetMapping("/plugins/catalog")
    public ApiResponse<List<PluginCatalogItem>> catalog(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(pluginApplicationService.listCatalog(), traceId);
    }

    @GetMapping("/plugins/catalog/{pluginCode}")
    public ApiResponse<PluginCatalogItem> catalogItem(@PathVariable String pluginCode,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(pluginApplicationService.getCatalog(pluginCode), traceId);
    }

    @GetMapping("/novels/{projectId}/plugins")
    public ApiResponse<List<PluginProjectInstall>> projectPlugins(@PathVariable Long projectId,
                                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(pluginApplicationService.listProjectInstalls(projectId), traceId);
    }

    @PostMapping("/novels/{projectId}/plugins/install")
    public ApiResponse<String> install(@PathVariable Long projectId,
                                       @Valid @RequestBody InstallPluginDto dto,
                                       @RequestParam("operatorId") Long operatorId,
                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        pluginApplicationService.install(
                projectId,
                new PluginCommands.InstallPluginCommand(dto.getPluginCode(), dto.getVersion(), dto.getConfigJson(), operatorId),
                traceId
        );
        return ApiResponse.success("installed", traceId);
    }

    @PatchMapping("/novels/{projectId}/plugins/{pluginCode}")
    public ApiResponse<String> updateInstall(@PathVariable Long projectId,
                                             @PathVariable String pluginCode,
                                             @RequestBody UpdatePluginInstallDto dto,
                                             @RequestParam("operatorId") Long operatorId,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        pluginApplicationService.updateInstall(
                projectId,
                pluginCode,
                new PluginCommands.UpdatePluginInstallCommand(dto.getEnabled(), dto.getConfigJson(), operatorId),
                traceId
        );
        return ApiResponse.success("updated", traceId);
    }

    @DeleteMapping("/novels/{projectId}/plugins/{pluginCode}")
    public ApiResponse<String> deleteInstall(@PathVariable Long projectId,
                                             @PathVariable String pluginCode,
                                             @RequestParam("operatorId") Long operatorId,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        pluginApplicationService.deleteInstall(projectId, pluginCode, operatorId, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    @GetMapping("/novels/{projectId}/plugins/call-logs")
    public ApiResponse<List<PluginCallLog>> callLogs(@PathVariable Long projectId,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(pluginApplicationService.listCallLogs(projectId), traceId);
    }
}

