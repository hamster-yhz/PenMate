package com.penmate.backend.application.agent;

import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolInvocationGatewayTest {

    @Test
    void UT_APP_AGENT_TOOL_INVOCATION_GATEWAY_RETURNS_WAITING_APPROVAL_AND_SAVES_PENDING_SNAPSHOT() {
        StaticToolMetadataRegistry registry = new StaticToolMetadataRegistry();
        DefaultApprovalPolicyEngine policyEngine = new DefaultApprovalPolicyEngine();
        ApprovalApplicationService approvalApplicationService = mock(ApprovalApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
        AtomicReference<Object> savedSnapshotRef = new AtomicReference<>();
        Object pendingRepository = newPendingRepositoryProxy(savedSnapshotRef);
        BookCrudAgentToolHandler bookCrudAgentToolHandler = new BookCrudAgentToolHandler(mock(NovelApplicationService.class));

        ApprovalRequest approvalRequest = new ApprovalRequest();
        approvalRequest.setId(88001L);
        approvalRequest.setApprovalType("BOOK_DELETE");
        when(approvalApplicationService.create(any(), eq("trace-approve-1"))).thenReturn(approvalRequest);
        when(agentRepository.updateGenerationTaskStatus(10001L, 8001L, "waiting_approval", null)).thenReturn(1);

        Object gateway = instantiateGateway(
                registry,
                policyEngine,
                approvalApplicationService,
                pendingRepository,
                agentRepository,
                realtimeEventService,
                List.of(bookCrudAgentToolHandler)
        );
        ToolInvocationRequest request = new ToolInvocationRequest(
                10001L,
                8001L,
                7001L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9001}",
                1001L,
                "trace-approve-1",
                "{}",
                "book-crud-delete-9001"
        );

        Object result = invokeGateway(gateway, request);

        assertThat(invokeAccessor(result, "status")).isEqualTo("WAITING_APPROVAL");
        assertThat(invokeAccessor(result, "approvalId")).isEqualTo(88001L);
        assertThat(savedSnapshotRef.get()).isNotNull();
        assertThat(invokeAccessor(savedSnapshotRef.get(), "approvalId")).isEqualTo(88001L);
        assertThat(invokeAccessor(savedSnapshotRef.get(), "toolCode")).isEqualTo("book_crud");
        verify(agentRepository).updateGenerationTaskStatus(10001L, 8001L, "waiting_approval", null);
        verify(realtimeEventService).publishGenerationWaitingApproval(10001L, 8001L, 88001L, "BOOK_DELETE");
    }

    @Test
    void UT_APP_AGENT_TOOL_INVOCATION_GATEWAY_DISPATCHES_BOOK_CRUD_CREATE_TO_HANDLER() {
        StaticToolMetadataRegistry registry = new StaticToolMetadataRegistry();
        DefaultApprovalPolicyEngine policyEngine = new DefaultApprovalPolicyEngine();
        ApprovalApplicationService approvalApplicationService = mock(ApprovalApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
        Object pendingRepository = newPendingRepositoryProxy(new AtomicReference<>());

        NovelApplicationService novelApplicationService = mock(NovelApplicationService.class);
        NovelProject created = new NovelProject();
        created.setProjectId(990301L);
        created.setTitle("星海档案");
        created.setSummary("太空歌剧");
        created.setStatus(1);
        when(novelApplicationService.createProject(any(), eq("trace-book-gateway-create"))).thenReturn(created);
        BookCrudAgentToolHandler bookCrudAgentToolHandler = new BookCrudAgentToolHandler(novelApplicationService);

        Object gateway = instantiateGateway(
                registry,
                policyEngine,
                approvalApplicationService,
                pendingRepository,
                agentRepository,
                realtimeEventService,
                List.of(bookCrudAgentToolHandler)
        );
        ToolInvocationRequest request = new ToolInvocationRequest(
                0L,
                8010L,
                7001L,
                "book_crud",
                "{\"operation\":\"create\",\"ownerUserId\":1001,\"title\":\"星海档案\",\"summary\":\"太空歌剧\",\"status\":1}",
                1001L,
                "trace-book-gateway-create",
                "{}",
                "book-crud-create-star-archive"
        );

        Object result = invokeGateway(gateway, request);

        assertThat(invokeAccessor(result, "status")).isEqualTo("SUCCESS");
        assertThat((String) invokeAccessor(result, "toolOutput")).contains("星海档案");
        verify(approvalApplicationService, never()).create(any(), any());
    }

    @Test
    void UT_APP_AGENT_TOOL_INVOCATION_GATEWAY_FAILS_WHEN_HANDLER_NOT_FOUND() {
        StaticToolMetadataRegistry registry = new StaticToolMetadataRegistry();
        DefaultApprovalPolicyEngine policyEngine = new DefaultApprovalPolicyEngine();
        ApprovalApplicationService approvalApplicationService = mock(ApprovalApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
        Object pendingRepository = newPendingRepositoryProxy(new AtomicReference<>());

        Object gateway = instantiateGateway(
                registry,
                policyEngine,
                approvalApplicationService,
                pendingRepository,
                agentRepository,
                realtimeEventService,
                List.of()
        );
        ToolInvocationRequest request = new ToolInvocationRequest(
                10001L,
                8014L,
                7001L,
                "context_enhancer",
                "{\"prompt\":\"补充冲突\"}",
                1001L,
                "trace-missing-handler",
                "{}",
                "context-enhancer-missing-handler"
        );

        Object result = invokeGateway(gateway, request);

        assertThat(invokeAccessor(result, "status")).isEqualTo("FAILED");
        assertThat(invokeAccessor(result, "errorCode")).isEqualTo("TOOL_HANDLER_NOT_FOUND");
    }

    @Test
    void UT_APP_AGENT_TOOL_INVOCATION_GATEWAY_FAILS_BEFORE_APPROVAL_WHEN_APPROVAL_TOOL_HANDLER_NOT_FOUND() {
        StaticToolMetadataRegistry registry = new StaticToolMetadataRegistry();
        DefaultApprovalPolicyEngine policyEngine = new DefaultApprovalPolicyEngine();
        ApprovalApplicationService approvalApplicationService = mock(ApprovalApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
        Object pendingRepository = newPendingRepositoryProxy(new AtomicReference<>());

        Object gateway = instantiateGateway(
                registry,
                policyEngine,
                approvalApplicationService,
                pendingRepository,
                agentRepository,
                realtimeEventService,
                List.of()
        );
        ToolInvocationRequest request = new ToolInvocationRequest(
                10001L,
                8016L,
                7001L,
                "book_crud",
                "{\"operation\":\"delete\",\"projectId\":9001}",
                1001L,
                "trace-approve-missing-handler",
                "{}",
                "book-crud-delete-missing-handler"
        );

        Object result = invokeGateway(gateway, request);

        assertThat(invokeAccessor(result, "status")).isEqualTo("FAILED");
        assertThat(invokeAccessor(result, "errorCode")).isEqualTo("TOOL_HANDLER_NOT_FOUND");
        verify(approvalApplicationService, never()).create(any(), any());
    }

    @Test
    void UT_APP_AGENT_TOOL_INVOCATION_GATEWAY_REJECTS_INVALID_BOOK_DELETE_BEFORE_CREATING_APPROVAL() {
        StaticToolMetadataRegistry registry = new StaticToolMetadataRegistry();
        DefaultApprovalPolicyEngine policyEngine = new DefaultApprovalPolicyEngine();
        ApprovalApplicationService approvalApplicationService = mock(ApprovalApplicationService.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
        Object pendingRepository = newPendingRepositoryProxy(new AtomicReference<>());
        BookCrudAgentToolHandler bookCrudAgentToolHandler = new BookCrudAgentToolHandler(mock(NovelApplicationService.class));

        Object gateway = instantiateGateway(
                registry,
                policyEngine,
                approvalApplicationService,
                pendingRepository,
                agentRepository,
                realtimeEventService,
                List.of(bookCrudAgentToolHandler)
        );
        ToolInvocationRequest request = new ToolInvocationRequest(
                10001L,
                8017L,
                7001L,
                "book_crud",
                "{\"operation\":\"delete\",\"bookId\":9001}",
                1001L,
                "trace-invalid-delete",
                "{}",
                "book-crud-invalid-delete"
        );

        Object result = invokeGateway(gateway, request);

        assertThat(invokeAccessor(result, "status")).isEqualTo("FAILED");
        assertThat(invokeAccessor(result, "errorCode")).isEqualTo("TOOL_VALIDATION_FAILED");
        verify(approvalApplicationService, never()).create(any(), any());
    }

    private Object instantiateGateway(StaticToolMetadataRegistry registry,
                                      DefaultApprovalPolicyEngine policyEngine,
                                      ApprovalApplicationService approvalApplicationService,
                                      Object pendingRepository,
                                      AgentRepository agentRepository,
                                      RealtimeEventService realtimeEventService,
                                      List<?> handlers) {
        try {
            Class<?> pendingRepoType = Class.forName("com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository");
            Class<?> gatewayType = Class.forName("com.penmate.backend.application.agent.ToolInvocationGateway");
            Constructor<?> constructor = gatewayType.getDeclaredConstructor(
                    StaticToolMetadataRegistry.class,
                    DefaultApprovalPolicyEngine.class,
                    ApprovalApplicationService.class,
                    pendingRepoType,
                    AgentRepository.class,
                    RealtimeEventService.class,
                    List.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    registry,
                    policyEngine,
                    approvalApplicationService,
                    pendingRepository,
                    agentRepository,
                    realtimeEventService,
                    handlers
            );
        } catch (Exception ex) {
            throw new AssertionError("expected gateway to be constructible", ex);
        }
    }

    private Object invokeGateway(Object gateway, ToolInvocationRequest request) {
        try {
            Method method = gateway.getClass().getMethod("invoke", ToolInvocationRequest.class);
            return method.invoke(gateway, request);
        } catch (Exception ex) {
            throw new AssertionError("expected gateway invocation to succeed", ex);
        }
    }

    private Object newPendingRepositoryProxy(AtomicReference<Object> savedSnapshotRef) {
        try {
            Class<?> pendingRepoType = Class.forName("com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository");
            return Proxy.newProxyInstance(
                    pendingRepoType.getClassLoader(),
                    new Class<?>[]{pendingRepoType},
                    (proxy, method, args) -> {
                        if ("save".equals(method.getName())) {
                            savedSnapshotRef.set(args[0]);
                            return null;
                        }
                        return null;
                    }
            );
        } catch (Exception ex) {
            throw new AssertionError("expected pending repository type to exist", ex);
        }
    }

    private Object invokeAccessor(Object target, String accessorName) {
        try {
            Method method = target.getClass().getMethod(accessorName);
            return method.invoke(target);
        } catch (Exception ex) {
            throw new AssertionError("expected accessor invocation to succeed: " + accessorName, ex);
        }
    }
}
