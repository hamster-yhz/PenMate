package com.penmate.backend.interfaces.api.agent;

import com.penmate.backend.application.agent.AgentApplicationService;
import com.penmate.backend.application.agent.command.AgentCommands;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.shared.service.GenerationStreamService;
import com.penmate.backend.interfaces.api.agent.dto.ApplyAgentGenerationDto;
import com.penmate.backend.interfaces.api.agent.dto.CreateAgentConversationDto;
import com.penmate.backend.interfaces.api.agent.dto.CreateAgentGenerationDto;
import com.penmate.backend.interfaces.api.agent.dto.CreateAgentMessageDto;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agent 会话与生成任务控制器。
 * <p>负责接入会话管理、消息写入、生成任务创建/应用及 SSE 订阅请求，并将请求参数映射为应用层命令对象。</p>
 */
@RestController
@RequestMapping("/api/v1/novels/{projectId}/agent")
@Slf4j
public class AgentController {

    private final AgentApplicationService agentApplicationService;
    private final GenerationStreamService generationStreamService;

    public AgentController(AgentApplicationService agentApplicationService,
                           GenerationStreamService generationStreamService) {
        this.agentApplicationService = agentApplicationService;
        this.generationStreamService = generationStreamService;
    }

    /**
     * 查询项目下的 Agent 会话列表。
     * <p><b>业务目的：</b>返回当前项目可继续对话的会话集合，供前端会话侧边栏展示。</p>
     * <p><b>流程主线：</b>接收项目参数 -> 调用应用服务查询会话 -> 统一封装 API 响应。</p>
     * <p><b>关键调用：</b>{@code agentApplicationService.listConversations(projectId)}。</p>
     * <p><b>异常与分支：</b>项目不存在或无权限时由应用层抛出业务异常并统一拦截。</p>
     * <p><b>副作用：</b>无状态写入。</p>
     */
    @GetMapping("/conversations")
    public ApiResponse<List<AgentConversation>> listConversations(@PathVariable Long projectId,
                                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentApplicationService.listConversations(projectId), traceId);
    }

    /**
     * 创建新的 Agent 会话。
     * <p><b>业务目的：</b>在指定项目下创建会话容器，承载后续消息与生成任务。</p>
     * <p><b>流程主线：</b>校验请求体 -> 组装 {@link AgentCommands.CreateConversationCommand} -> 调用应用服务创建 -> 返回会话实体。</p>
     * <p><b>关键调用：</b>{@code agentApplicationService.createConversation(...)} 完成会话持久化与审计处理。</p>
     * <p><b>异常与分支：</b>参数非法或操作者无权限时返回业务错误。</p>
     * <p><b>副作用：</b>写入会话记录。</p>
     */
    @PostMapping("/conversations")
    public ApiResponse<AgentConversation> createConversation(@PathVariable Long projectId,
                                                             @Valid @RequestBody CreateAgentConversationDto dto,
                                                             @RequestParam("operatorId") Long operatorId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentApplicationService.createConversation(
                projectId,
                new AgentCommands.CreateConversationCommand(
                        dto.getUserId(),
                        dto.getTitle(),
                        dto.getContextScopeJson(),
                        dto.getStatus(),
                        operatorId
                ),
                traceId
        ), traceId);
    }

    /**
     * 查询会话消息列表。
     * <p><b>业务目的：</b>获取指定会话历史消息，用于恢复上下文与渲染聊天记录。</p>
     * <p><b>流程主线：</b>读取项目与会话参数 -> 调用应用服务查询消息 -> 返回按存储顺序组织的消息列表。</p>
     * <p><b>关键调用：</b>{@code agentApplicationService.listMessages(projectId, conversationId)}。</p>
     * <p><b>异常与分支：</b>会话不属于项目或不存在时由应用层返回错误。</p>
     * <p><b>副作用：</b>无状态写入。</p>
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<AgentMessage>> listMessages(@PathVariable Long projectId,
                                                        @PathVariable Long conversationId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentApplicationService.listMessages(projectId, conversationId), traceId);
    }

    /**
     * 向会话追加一条消息。
     * <p><b>业务目的：</b>写入用户或系统消息，作为后续生成任务的上下文输入。</p>
     * <p><b>流程主线：</b>校验消息入参 -> 组装 {@link AgentCommands.CreateMessageCommand} -> 调用应用服务写入 -> 返回新消息。</p>
     * <p><b>关键调用：</b>{@code agentApplicationService.createMessage(...)}，内部会处理消息归属与状态字段。</p>
     * <p><b>异常与分支：</b>会话不存在、角色非法或操作者无权限时返回业务异常。</p>
     * <p><b>副作用：</b>新增消息记录。</p>
     */
    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<AgentMessage> createMessage(@PathVariable Long projectId,
                                                   @PathVariable Long conversationId,
                                                   @Valid @RequestBody CreateAgentMessageDto dto,
                                                   @RequestParam("operatorId") Long operatorId,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentApplicationService.createMessage(
                projectId,
                conversationId,
                new AgentCommands.CreateMessageCommand(
                        dto.getRole(),
                        dto.getUserMessageType(),
                        dto.getContentMd(),
                        dto.getAttachmentsJson(),
                        dto.getToolCallsJson(),
                        operatorId
                ),
                traceId
        ), traceId);
    }

    /**
     * 创建 Agent 生成任务。
     * <p><b>业务目的：</b>基于会话上下文、章节目标和显式模型配置创建可执行生成任务。</p>
     * <p><b>流程主线：</b>解析请求参数 -> 组装 {@link AgentCommands.CreateGenerationCommand} -> 调用应用服务创建任务 -> 返回任务快照。</p>
     * <p><b>关键调用：</b>{@code agentApplicationService.createGeneration(...)}，由应用层负责后续编排与状态流转。</p>
     * <p><b>异常与分支：</b>模型配置不可用、章节不存在或参数不合法时返回业务错误。</p>
     * <p><b>副作用：</b>写入生成任务记录，可能触发异步编排链路。</p>
     */
    @PostMapping("/generations")
    public ApiResponse<AgentGenerationTask> createGeneration(@PathVariable Long projectId,
                                                             @Valid @RequestBody CreateAgentGenerationDto dto,
                                                             @RequestParam("operatorId") Long operatorId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentApplicationService.createGeneration(
                projectId,
                new AgentCommands.CreateGenerationCommand(
                        dto.getConversationId(),
                        dto.getChapterId(),
                        dto.getModelConfigId(),
                        dto.getTaskType(),
                        dto.getPromptSnapshot(),
                        dto.getStyleProfileSnapshot(),
                        dto.getPluginSnapshot(),
                        operatorId
                ),
                traceId
        ), traceId);
    }

    /**
     * 查询生成任务详情。
     * <p><b>业务目的：</b>返回任务当前状态、生成结果和元信息，用于轮询刷新任务面板。</p>
     * <p><b>流程主线：</b>接收任务标识 -> 调用应用服务获取任务 -> 统一响应输出。</p>
     * <p><b>关键调用：</b>{@code agentApplicationService.getGeneration(projectId, taskId)}。</p>
     * <p><b>异常与分支：</b>任务不存在或跨项目访问时返回业务异常。</p>
     * <p><b>副作用：</b>无状态写入。</p>
     */
    @GetMapping("/generations/{taskId}")
    public ApiResponse<AgentGenerationTask> getGeneration(@PathVariable Long projectId,
                                                           @PathVariable Long taskId,
                                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentApplicationService.getGeneration(projectId, taskId), traceId);
    }

    /**
     * 应用生成结果到目标业务对象（如章节内容）。
     * <p><b>业务目的：</b>将生成任务产出从“可预览”状态转为“已应用”状态，完成业务落库。</p>
     * <p><b>流程主线：</b>读取 applyNote -> 组装 {@link AgentCommands.ApplyGenerationCommand} -> 调用应用服务执行应用 -> 返回更新后的任务状态。</p>
     * <p><b>关键调用：</b>{@code agentApplicationService.applyGeneration(...)}，由应用层处理幂等与状态机校验。</p>
     * <p><b>异常与分支：</b>任务状态不允许应用、目标对象不存在或操作者无权限时返回业务错误。</p>
     * <p><b>副作用：</b>更新任务状态并写入目标业务数据。</p>
     */
    @PostMapping("/generations/{taskId}/apply")
    public ApiResponse<AgentGenerationTask> applyGeneration(@PathVariable Long projectId,
                                                             @PathVariable Long taskId,
                                                             @RequestBody(required = false) ApplyAgentGenerationDto dto,
                                                             @RequestParam("operatorId") Long operatorId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentApplicationService.applyGeneration(
                projectId,
                taskId,
                new AgentCommands.ApplyGenerationCommand(operatorId, dto == null ? null : dto.getApplyNote()),
                traceId
        ), traceId);
    }

    /**
     * 订阅生成任务的 SSE 流式事件。
     * <p><b>业务目的：</b>向前端持续推送任务进度与 token 流，支持实时生成体验。</p>
     * <p><b>流程主线：</b>记录订阅日志 -> 校验任务归属与可访问性 -> 打开任务对应 SSE 通道并返回。</p>
     * <p><b>关键调用：</b>{@code agentApplicationService.getGeneration(...)} 用于校验任务存在；{@code generationStreamService.openStream(taskId)} 建立流。</p>
     * <p><b>异常与分支：</b>任务不存在时在校验阶段返回错误，避免无效连接建立。</p>
     * <p><b>副作用：</b>创建 SSE 连接资源。</p>
     */
    @GetMapping(value = "/generations/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGeneration(@PathVariable Long projectId,
                                       @PathVariable Long taskId,
                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        log.info("SSE subscribe request: projectId={}, taskId={}, traceId={}", projectId, taskId, traceId);
        agentApplicationService.getGeneration(projectId, taskId);
        return generationStreamService.openStream(taskId);
    }
}

