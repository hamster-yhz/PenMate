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

/**
 * 插件目录与项目安装控制器。
 * <p>负责插件目录查询、项目插件安装/更新/卸载以及插件调用日志查询接口。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class PluginController {

    private final PluginApplicationService pluginApplicationService;

    public PluginController(PluginApplicationService pluginApplicationService) {
        this.pluginApplicationService = pluginApplicationService;
    }

    /**
     * 查询插件市场目录。
     * <p><b>业务目的：</b>返回当前系统可安装插件清单，供工作台展示与筛选。</p>
     * <p><b>流程主线：</b>接收请求 -> 调用应用服务读取插件目录 -> 封装统一响应。</p>
     * <p><b>关键调用：</b>{@code pluginApplicationService.listCatalog()}。</p>
     * <p><b>异常与分支：</b>目录为空时返回空列表而非错误。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     *
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/plugins/catalog")
    public ApiResponse<List<PluginCatalogItem>> catalog(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(pluginApplicationService.listCatalog(), traceId);
    }

    /**
     * 查询单个插件目录详情。
     * <p><b>业务目的：</b>根据插件编码返回插件定义、版本与能力描述。</p>
     * <p><b>流程主线：</b>读取插件编码 -> 调用应用服务查询详情 -> 封装响应。</p>
     * <p><b>关键调用：</b>{@code pluginApplicationService.getCatalog(pluginCode)}。</p>
     * <p><b>异常与分支：</b>插件不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     *
     * @param pluginCode 入参：pluginCode
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/plugins/catalog/{pluginCode}")
    public ApiResponse<PluginCatalogItem> catalogItem(@PathVariable String pluginCode,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(pluginApplicationService.getCatalog(pluginCode), traceId);
    }

    /**
     * 查询项目已安装插件列表。
     * <p><b>业务目的：</b>返回项目级插件安装状态与配置，用于插件管理面板展示。</p>
     * <p><b>流程主线：</b>读取项目ID -> 调用应用服务查询安装记录 -> 返回列表。</p>
     * <p><b>关键调用：</b>{@code pluginApplicationService.listProjectInstalls(projectId)}。</p>
     * <p><b>异常与分支：</b>项目不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     *
     * @param projectId 入参：projectId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/novels/{projectId}/plugins")
    public ApiResponse<List<PluginProjectInstall>> projectPlugins(@PathVariable Long projectId,
                                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(pluginApplicationService.listProjectInstalls(projectId), traceId);
    }

    /**
     * 安装插件到指定项目。
     * <p><b>业务目的：</b>将插件目录项安装到项目空间，使后续 Agent 或业务流程可调用该插件能力。</p>
     * <p><b>流程主线：</b>校验安装参数 -> 组装 {@link PluginCommands.InstallPluginCommand} -> 调用应用服务执行安装 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code pluginApplicationService.install(...)} 负责安装记录写入与初始化配置。</p>
     * <p><b>异常与分支：</b>插件不存在、版本不可用或重复安装时返回业务异常。</p>
     * <p><b>副作用：</b>新增项目插件安装记录。</p>
     *
     * @param projectId 入参：projectId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
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

    /**
     * 更新项目插件安装配置。
     * <p><b>业务目的：</b>调整插件启用状态与配置 JSON，使插件行为与项目需求一致。</p>
     * <p><b>流程主线：</b>接收更新参数 -> 组装 {@link PluginCommands.UpdatePluginInstallCommand} -> 调用应用服务更新 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code pluginApplicationService.updateInstall(...)}。</p>
     * <p><b>异常与分支：</b>插件未安装、配置非法或操作者无权限时返回业务异常。</p>
     * <p><b>副作用：</b>更新插件安装状态与配置。</p>
     *
     * @param projectId 入参：projectId
     * @param pluginCode 入参：pluginCode
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
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

    /**
     * 卸载项目插件。
     * <p><b>业务目的：</b>移除项目对插件的绑定，阻止后续继续调用该插件。</p>
     * <p><b>流程主线：</b>读取项目与插件标识 -> 调用应用服务执行卸载 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code pluginApplicationService.deleteInstall(projectId, pluginCode, operatorId, traceId)}。</p>
     * <p><b>异常与分支：</b>插件未安装或操作者无权限时返回业务异常。</p>
     * <p><b>副作用：</b>删除或失效项目插件安装记录。</p>
     *
     * @param projectId 入参：projectId
     * @param pluginCode 入参：pluginCode
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @DeleteMapping("/novels/{projectId}/plugins/{pluginCode}")
    public ApiResponse<String> deleteInstall(@PathVariable Long projectId,
                                             @PathVariable String pluginCode,
                                             @RequestParam("operatorId") Long operatorId,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        pluginApplicationService.deleteInstall(projectId, pluginCode, operatorId, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 查询项目插件调用日志。
     * <p><b>业务目的：</b>返回插件调用轨迹，支持问题排查与审计分析。</p>
     * <p><b>流程主线：</b>读取项目ID -> 调用应用服务查询调用日志 -> 返回日志列表。</p>
     * <p><b>关键调用：</b>{@code pluginApplicationService.listCallLogs(projectId)}。</p>
     * <p><b>异常与分支：</b>项目不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     *
     * @param projectId 入参：projectId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/novels/{projectId}/plugins/call-logs")
    public ApiResponse<List<PluginCallLog>> callLogs(@PathVariable Long projectId,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(pluginApplicationService.listCallLogs(projectId), traceId);
    }
}

