package com.penmate.backend.application.plugin;

import com.penmate.backend.application.plugin.command.PluginCommands.InstallPluginCommand;
import com.penmate.backend.application.plugin.command.PluginCommands.UpdatePluginInstallCommand;
import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginCatalogItem;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import com.penmate.backend.domain.plugin.repository.PluginRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 插件应用服务。
 * <p>负责插件目录查询、项目安装配置管理以及插件调用日志查询。</p>
 */
@Service
@Slf4j
public class PluginApplicationService {

    private final PluginRepository pluginRepository;
    private final BusinessIdGenerator businessIdGenerator;

    public PluginApplicationService(PluginRepository pluginRepository,
                                    BusinessIdGenerator businessIdGenerator) {
        this.pluginRepository = pluginRepository;
        this.businessIdGenerator = businessIdGenerator;
    }

    /**
     * 查询插件市场目录。
     *
     * @return 出参：处理结果
     */
    public List<PluginCatalogItem> listCatalog() {
        List<PluginCatalogItem> catalog = pluginRepository.listCatalog();
        log.info("查询插件目录: count={}", catalog.size());
        return catalog;
    }

    /**
     * 按插件编码查询插件目录详情。
     *
     * @param pluginCode 入参：pluginCode
     * @return 出参：处理结果
     */
    public PluginCatalogItem getCatalog(String pluginCode) {
        log.info("查询插件目录详情: pluginCode={}", pluginCode);
        PluginCatalogItem item = pluginRepository.getCatalogByCode(pluginCode);
        if (item == null) {
            log.warn("查询插件目录详情失败: pluginCode={}, reason=not_found", pluginCode);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Plugin not found");
        }
        log.info("查询插件目录详情成功: pluginCode={}, pluginId={}", pluginCode, item.getId());
        return item;
    }

    /**
     * 查询项目已安装插件列表。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<PluginProjectInstall> listProjectInstalls(Long projectId) {
        List<PluginProjectInstall> installs = pluginRepository.listProjectInstalls(projectId);
        log.info("查询项目插件安装列表: projectId={}, count={}", projectId, installs.size());
        return installs;
    }

    /**
     * 在项目中安装插件。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void install(Long projectId, InstallPluginCommand command, String traceId) {
        log.info("安装插件: projectId={}, pluginCode={}, version={}, operatorId={}",
                projectId, command.pluginCode(), command.version(), command.operatorId());
        Long pluginId = pluginRepository.findCatalogIdByCode(command.pluginCode());
        if (pluginId == null) {
            log.warn("安装插件失败: projectId={}, pluginCode={}, reason=plugin_not_found", projectId, command.pluginCode());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Plugin not found");
        }
        int affected = pluginRepository.insertInstall(
                businessIdGenerator.nextId(),
                projectId,
                pluginId,
                command.version(),
                command.configJson(),
                true,
                command.operatorId()
        );
        if (affected < 1) {
            log.error("安装插件失败: projectId={}, pluginCode={}, pluginId={}, reason=insert_failed", projectId, command.pluginCode(), pluginId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to install plugin");
        }
        writeAudit(traceId, command.operatorId(), "plugin", "install-plugin", "plugin_project_installs", pluginId.toString(), command.configJson(), 200);
        log.info("安装插件成功: projectId={}, pluginCode={}, pluginId={}", projectId, command.pluginCode(), pluginId);
    }

    /**
     * 更新项目插件安装配置（启用状态与配置项）。
     *
     * @param projectId 入参：projectId
     * @param pluginCode 入参：pluginCode
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void updateInstall(Long projectId, String pluginCode, UpdatePluginInstallCommand command, String traceId) {
        log.info("更新插件安装配置: projectId={}, pluginCode={}, enabled={}, operatorId={}",
                projectId, pluginCode, command.enabled(), command.operatorId());
        int affected = pluginRepository.updateInstall(projectId, pluginCode, command.enabled(), command.configJson());
        if (affected != 1) {
            log.warn("更新插件安装配置失败: projectId={}, pluginCode={}, reason=not_found", projectId, pluginCode);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Plugin install not found");
        }
        writeAudit(traceId, command.operatorId(), "plugin", "update-plugin-install", "plugin_project_installs", pluginCode, command.configJson(), 200);
        log.info("更新插件安装配置成功: projectId={}, pluginCode={}", projectId, pluginCode);
    }

    /**
     * 卸载项目中的插件安装记录。
     *
     * @param projectId 入参：projectId
     * @param pluginCode 入参：pluginCode
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteInstall(Long projectId, String pluginCode, Long operatorId, String traceId) {
        log.info("卸载插件: projectId={}, pluginCode={}, operatorId={}", projectId, pluginCode, operatorId);
        int affected = pluginRepository.deleteInstall(projectId, pluginCode);
        if (affected != 1) {
            log.warn("卸载插件失败: projectId={}, pluginCode={}, reason=not_found", projectId, pluginCode);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Plugin install not found");
        }
        writeAudit(traceId, operatorId, "plugin", "delete-plugin-install", "plugin_project_installs", pluginCode, null, 200);
        log.info("卸载插件成功: projectId={}, pluginCode={}", projectId, pluginCode);
    }

    /**
     * 查询项目插件调用日志。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<PluginCallLog> listCallLogs(Long projectId) {
        List<PluginCallLog> logs = pluginRepository.listCallLogs(projectId);
        log.info("查询插件调用日志: projectId={}, count={}", projectId, logs.size());
        return logs;
    }

    public void recordToolCall(PluginCallLog callLog) {
        if (callLog.getPluginCallLogId() == null) {
            callLog.setPluginCallLogId(businessIdGenerator.nextId());
        }
        int affected = pluginRepository.insertCallLog(callLog);
        if (affected != 1) {
            log.warn("写入插件调用日志失败: projectId={}, pluginCode={}, toolName={}",
                    callLog.getProjectId(), callLog.getPluginCode(), callLog.getToolName());
        }
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        // 审计模块已移除
    }
}


