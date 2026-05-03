package com.penmate.backend.interfaces.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.command.AgentCommands;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.usecase.AgentGenerationAppService;
import com.penmate.backend.application.agent.usecase.AgentMessageAppService;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentConversation;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.shared.service.GenerationStreamService;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentConversationAppService agentConversationAppService;

    @Mock
    private AgentMessageAppService agentMessageAppService;

    @Mock
    private AgentGenerationAppService agentGenerationAppService;

    @Mock
    private GenerationStreamService generationStreamService;

    @InjectMocks
    private AgentController agentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(agentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void UT_AGENT_CONVERSATION_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AGENT-CONV-LIST";
        AgentConversation conv = new AgentConversation();
        conv.setId(7001L);
        conv.setTitle("第一卷讨论");
        when(agentConversationAppService.listConversations(10001L)).thenReturn(List.of(conv));

        mockMvc().perform(get("/api/v1/novels/10001/agent/conversations")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(7001))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_AGENT_CONVERSATION_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AGENT-CONV-CREATE";
        AgentConversation conv = new AgentConversation();
        conv.setId(7002L);
        when(agentConversationAppService.createConversation(eq(10001L), any(AgentCommands.CreateConversationCommand.class), eq(traceId))).thenReturn(conv);

        mockMvc().perform(post("/api/v1/novels/10001/agent/conversations")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", 1001,
                                "title", "终章修订"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7002));
    }

    @Test
    void UT_AGENT_MESSAGE_PARAM_INVALID() throws Exception {
        String traceId = "UT-TRACE-AGENT-MSG-INVALID";

        mockMvc().perform(post("/api/v1/novels/10001/agent/conversations/7001/messages")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "role", "",
                                "contentMd", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void UT_AGENT_GENERATION_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AGENT-GEN-CREATE";
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(8001L);
        when(agentGenerationAppService.createGeneration(eq(10001L), any(AgentCommands.CreateGenerationCommand.class), eq(traceId))).thenReturn(task);

        mockMvc().perform(post("/api/v1/novels/10001/agent/generations")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "conversationId", 7001,
                                "modelConfigId", 9001,
                                "taskType", "draft"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8001));
    }

    @Test
    void UT_AGENT_GENERATION_APPLY_CONFLICT() throws Exception {
        String traceId = "UT-TRACE-AGENT-GEN-APPLY-CONFLICT";
        doThrow(new IllegalArgumentException("Generation task is not applicable"))
                .when(agentGenerationAppService).applyGeneration(eq(10001L), eq(8001L), any(AgentCommands.ApplyGenerationCommand.class), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/agent/generations/8001/apply")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of("applyNote", "accept"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void UT_AGENT_GENERATION_APPLY_STATE_TRANSITION_INVALID() throws Exception {
        String traceId = "UT-TRACE-AGENT-GEN-STATE-INVALID";
        doThrow(BusinessException.of(HttpStatus.UNPROCESSABLE_ENTITY,
                "AGENT_STATE_TRANSITION_INVALID",
                "Invalid generation task state transition",
                null))
                .when(agentGenerationAppService).applyGeneration(eq(10001L), eq(8001L), any(AgentCommands.ApplyGenerationCommand.class), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/agent/generations/8001/apply")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of("applyNote", "accept"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("AGENT_STATE_TRANSITION_INVALID"));
    }

    @Test
    void UT_AGENT_SSE_STREAM_CONNECT_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AGENT-SSE-CONNECT";
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(8002L);
        when(agentGenerationAppService.getGeneration(10001L, 8002L)).thenReturn(task);
        when(generationStreamService.openStream(8002L)).thenReturn(new SseEmitter(5000L));

        mockMvc().perform(get("/api/v1/novels/10001/agent/generations/8002/stream")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk());
    }

    @Test
    void UT_AGENT_MESSAGE_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AGENT-MSG-CREATE";
        AgentMessage message = new AgentMessage();
        message.setId(7101L);
        message.setRole("user");
        when(agentMessageAppService.createMessage(eq(10001L), eq(7001L), any(AgentCommands.CreateMessageCommand.class), eq(traceId))).thenReturn(message);

        mockMvc().perform(post("/api/v1/novels/10001/agent/conversations/7001/messages")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "role", "user",
                                "contentMd", "继续扩写",
                                "userMessageType", "instruction"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7101))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_AGENT_GENERATION_APPLY_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-AGENT-GEN-APPLY-SUCCESS";
        AgentGenerationTask applied = new AgentGenerationTask();
        applied.setId(8001L);
        applied.setStatus("applied");
        when(agentGenerationAppService.applyGeneration(eq(10001L), eq(8001L), any(AgentCommands.ApplyGenerationCommand.class), eq(traceId))).thenReturn(applied);

        mockMvc().perform(post("/api/v1/novels/10001/agent/generations/8001/apply")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of("applyNote", "apply"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8001))
                .andExpect(jsonPath("$.data.status").value("applied"));
    }

    @Test
    void UT_AGENT_SSE_STREAM_TOKEN_ORDER() throws Exception {
        String traceId = "UT-TRACE-AGENT-SSE-TOKEN-ORDER";
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(8003L);
        when(agentGenerationAppService.getGeneration(10001L, 8003L)).thenReturn(task);
        when(generationStreamService.openStream(8003L)).thenReturn(new SseEmitter(5000L));

        mockMvc().perform(get("/api/v1/novels/10001/agent/generations/8003/stream")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk());
    }

    @Test
    void UT_AGENT_SSE_STREAM_DONE_EVENT() throws Exception {
        String traceId = "UT-TRACE-AGENT-SSE-DONE";
        AgentGenerationTask task = new AgentGenerationTask();
        task.setId(8004L);
        when(agentGenerationAppService.getGeneration(10001L, 8004L)).thenReturn(task);
        when(generationStreamService.openStream(8004L)).thenReturn(new SseEmitter(5000L));

        mockMvc().perform(get("/api/v1/novels/10001/agent/generations/8004/stream")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk());
    }
}
