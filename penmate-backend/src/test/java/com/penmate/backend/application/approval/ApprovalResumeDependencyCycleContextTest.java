package com.penmate.backend.application.approval;

import com.penmate.backend.application.agent.AgentTaskStateMachine;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.ToolApprovalViewFactory;
import com.penmate.backend.application.agent.tool.gateway.ToolCallApplicationService;
import com.penmate.backend.application.agent.tool.handler.AgentToolHandler;
import com.penmate.backend.application.agent.tool.runtime.ToolCallExecutionService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResumeService;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.agent.tool.runtime.ToolCallSnapshotMapper;
import com.penmate.backend.application.approval.coordination.AgentApprovalResumeCoordinator;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ApprovalResumeDependencyCycleContextTest.TestConfig.class)
class ApprovalResumeDependencyCycleContextTest {

    @Autowired
    private ToolCallApplicationService toolCallApplicationService;

    @Autowired
    private ApprovalApplicationService approvalApplicationService;

    @Autowired
    private ApprovedToolInvocationAsyncResumer approvedToolInvocationAsyncResumer;

    @Autowired
    private AgentApprovalResumeCoordinator agentApprovalResumeCoordinator;

    @Autowired
    private ToolCallResumeService toolCallResumeService;

    @Autowired
    private ToolCallExecutionService toolCallExecutionService;

    @MockBean
    private ApprovalRequestRepository approvalRequestRepository;

    @MockBean
    private AgentRepository agentRepository;

    @MockBean
    private PendingToolInvocationRepository pendingToolInvocationRepository;

    @MockBean
    private RealtimeEventService realtimeEventService;

    @MockBean
    private AgentLlmGateway agentLlmGateway;

    @MockBean
    private AgentModelRoutingService agentModelRoutingService;

    @MockBean
    private AgentToolDefinitionSource agentToolDefinitionSource;

    @Test
    void IT_APP_APPROVAL_RESUME_DEPENDENCY_GRAPH_SHOULD_LOAD_WITHOUT_CIRCULAR_REFERENCE() {
        assertThat(toolCallApplicationService).isNotNull();
        assertThat(approvalApplicationService).isNotNull();
        assertThat(approvedToolInvocationAsyncResumer).isNotNull();
        assertThat(agentApprovalResumeCoordinator).isNotNull();
        assertThat(toolCallResumeService).isNotNull();
        assertThat(toolCallExecutionService).isNotNull();
    }

    @TestConfiguration
    @Import({
            ToolCallApplicationService.class,
            ApprovalApplicationService.class,
            ApprovedToolInvocationAsyncResumer.class,
            AgentApprovalResumeCoordinator.class,
            ToolCallResumeService.class,
            ToolCallExecutionService.class,
            ToolCallSnapshotMapper.class,
            ToolApprovalViewFactory.class,
            DefaultApprovalPolicyEngine.class,
            AgentTaskStateMachine.class
    })
    static class TestConfig {

        @Bean
        AgentToolHandler testAgentToolHandler() {
            return new AgentToolHandler() {
                @Override
                public String toolCode() {
                    return "test_tool";
                }

                @Override
                public ToolCallResult execute(com.penmate.backend.application.agent.tool.runtime.ToolCallRequest request) {
                    return ToolCallResult.success("ok");
                }
            };
        }
    }
}
