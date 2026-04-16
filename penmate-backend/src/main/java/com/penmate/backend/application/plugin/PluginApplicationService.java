package com.penmate.backend.application.plugin;

import com.penmate.backend.application.plugin.command.PluginCommands.InstallPluginCommand;
import com.penmate.backend.application.plugin.command.PluginCommands.UpdatePluginInstallCommand;
import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginCatalogItem;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import com.penmate.backend.domain.plugin.repository.PluginRepository;
import com.penmate.backend.domain.shared.service.AuditService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * PluginApplicationService。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
@Service
public class PluginApplicationService {

    private final PluginRepository pluginRepository;
    private final AuditService auditService;

    public PluginApplicationService(PluginRepository pluginRepository,
                                    AuditService auditService) {
        this.pluginRepository = pluginRepository;
        this.auditService = auditService;
    }

    /**
     * 查询列表数据。
     *
     * @return 出参：处理结果
     */
    public List<PluginCatalogItem> listCatalog() {
        return pluginRepository.listCatalog();
    }

    /**
     * 查询详情数据。
     *
     * @param pluginCode 入参：pluginCode
     * @return 出参：处理结果
     */
    public PluginCatalogItem getCatalog(String pluginCode) {
        PluginCatalogItem item = pluginRepository.getCatalogByCode(pluginCode);
        if (item == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Plugin not found");
        }
        return item;
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<PluginProjectInstall> listProjectInstalls(Long projectId) {
        return pluginRepository.listProjectInstalls(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void install(Long projectId, InstallPluginCommand command, String traceId) {
        Long pluginId = pluginRepository.findCatalogIdByCode(command.pluginCode());
        if (pluginId == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Plugin not found");
        }
        int affected = pluginRepository.insertInstall(
                projectId,
                pluginId,
                command.version(),
                command.configJson(),
                true,
                command.operatorId()
        );
        if (affected < 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to install plugin");
        }
        writeAudit(traceId, command.operatorId(), "plugin", "install-plugin", "plugin_project_installs", pluginId.toString(), command.configJson(), 200);
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param pluginCode 入参：pluginCode
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void updateInstall(Long projectId, String pluginCode, UpdatePluginInstallCommand command, String traceId) {
        int affected = pluginRepository.updateInstall(projectId, pluginCode, command.enabled(), command.configJson());
        if (affected != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Plugin install not found");
        }
        writeAudit(traceId, command.operatorId(), "plugin", "update-plugin-install", "plugin_project_installs", pluginCode, command.configJson(), 200);
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param pluginCode 入参：pluginCode
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteInstall(Long projectId, String pluginCode, Long operatorId, String traceId) {
        int affected = pluginRepository.deleteInstall(projectId, pluginCode);
        if (affected != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Plugin install not found");
        }
        writeAudit(traceId, operatorId, "plugin", "delete-plugin-install", "plugin_project_installs", pluginCode, null, 200);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<PluginCallLog> listCallLogs(Long projectId) {
        return pluginRepository.listCallLogs(projectId);
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        String finalTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        auditService.write(finalTraceId, userId, module, action, resourceType, resourceId, requestJson, responseCode);
    }
}


