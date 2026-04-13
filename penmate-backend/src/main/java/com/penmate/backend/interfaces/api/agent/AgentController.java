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

@RestController
@RequestMapping("/api/v1/novels/{projectId}/agent")
public class AgentController {

    private final AgentApplicationService agentApplicationService;
    private final GenerationStreamService generationStreamService;

    public AgentController(AgentApplicationService agentApplicationService,
                           GenerationStreamService generationStreamService) {
        this.agentApplicationService = agentApplicationService;
        this.generationStreamService = generationStreamService;
    }

    @GetMapping("/conversations")
    public ApiResponse<List<AgentConversation>> listConversations(@PathVariable Long projectId,
                                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentApplicationService.listConversations(projectId), traceId);
    }

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

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<AgentMessage>> listMessages(@PathVariable Long projectId,
                                                        @PathVariable Long conversationId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentApplicationService.listMessages(projectId, conversationId), traceId);
    }

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
                        dto.getTaskType(),
                        dto.getPromptSnapshot(),
                        dto.getStyleProfileSnapshot(),
                        dto.getPluginSnapshot(),
                        operatorId
                ),
                traceId
        ), traceId);
    }

    @GetMapping("/generations/{taskId}")
    public ApiResponse<AgentGenerationTask> getGeneration(@PathVariable Long projectId,
                                                           @PathVariable Long taskId,
                                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(agentApplicationService.getGeneration(projectId, taskId), traceId);
    }

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

    @GetMapping(value = "/generations/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGeneration(@PathVariable Long projectId,
                                       @PathVariable Long taskId,
                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        agentApplicationService.getGeneration(projectId, taskId);
        return generationStreamService.openStream(taskId);
    }
}

