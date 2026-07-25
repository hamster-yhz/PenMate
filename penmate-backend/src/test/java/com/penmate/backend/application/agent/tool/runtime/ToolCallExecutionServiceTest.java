package com.penmate.backend.application.agent.tool.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.tool.handler.AgentToolHandler;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.ToolExposure;
import com.penmate.backend.application.agent.tool.definition.ToolGovernancePolicy;
import com.penmate.backend.application.agent.tool.definition.ToolLifecycleStatus;
import com.penmate.backend.application.agent.tool.definition.ToolPresentation;
import com.penmate.backend.application.approval.ApprovalPolicyDecision;
import com.penmate.backend.domain.agent.run.model.AgentToolCallExecution;
import com.penmate.backend.domain.agent.run.model.AgentToolCallExecutionStatus;
import com.penmate.backend.domain.agent.run.repository.AgentToolCallExecutionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.penmate.backend.infrastructure.agent.tool.NetworkntAgentToolSchemaValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCallExecutionServiceTest {

    private final InMemoryExecutions executions = new InMemoryExecutions();
    private final AtomicLong nextId = new AtomicLong(1000L);
    private final BusinessIdGenerator ids = nextId::incrementAndGet;
    private final AgentToolMutationGuard guard = mock(AgentToolMutationGuard.class);
    private final AgentRunExecutionContextResolver executionContexts = mock(AgentRunExecutionContextResolver.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successful_call_is_replayed_without_executing_handler_twice() {
        AgentToolHandler handler = handler(false, request -> ToolCallResult.success("{\"ok\":true}"));
        ToolCallExecutionService service = service(handler);
        ToolCallRequest request = request("call-1", "{\"b\":2,\"a\":1}", 7L);

        ToolCallResult first = service.execute(request);
        ToolCallResult replay = service.execute(request("call-1", "{\"a\":1,\"b\":2}", 7L));

        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(replay).isEqualTo(first);
        assertThat(((CountingHandler) handler).calls()).isEqualTo(1);
        assertThat(executions.find(11L, "call-1").status()).isEqualTo(AgentToolCallExecutionStatus.SUCCEEDED);
    }

    @Test
    void reused_tool_call_id_with_different_intent_is_rejected() {
        AgentToolHandler handler = handler(false, request -> ToolCallResult.success("ok"));
        ToolCallExecutionService service = service(handler);

        assertThat(service.execute(request("call-2", "{\"value\":1}", 7L)).status()).isEqualTo("SUCCESS");
        ToolCallResult mismatch = service.execute(request("call-2", "{\"value\":2}", 7L));

        assertThat(mismatch.errorCode()).isEqualTo("TOOL_CALL_REQUEST_MISMATCH");
        assertThat(((CountingHandler) handler).calls()).isEqualTo(1);
    }

    @Test
    void started_call_from_older_execution_token_becomes_ambiguous_and_is_not_rerun() {
        executions.tryInsertStarted(AgentToolCallExecution.started(
                900L, 11L, "call-3", "test_tool", "a".repeat(64), 6L, Instant.now()));
        AgentToolHandler handler = handler(false, request -> ToolCallResult.success("should-not-run"));
        ToolCallExecutionService service = service(handler);
        ToolCallRequest request = request("call-3", "{}", 7L);
        String hash = hashFromFreshClaim(request);
        executions.replaceStartedHash(11L, "call-3", hash);

        ToolCallResult result = service.execute(request);

        assertThat(result.errorCode()).isEqualTo("TOOL_CALL_AMBIGUOUS");
        assertThat(executions.find(11L, "call-3").status()).isEqualTo(AgentToolCallExecutionStatus.AMBIGUOUS);
        assertThat(((CountingHandler) handler).calls()).isZero();
    }

    @Test
    void same_token_concurrent_duplicate_observes_in_progress_and_executes_once() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AgentToolHandler handler = handler(false, request -> {
            entered.countDown();
            await(release);
            return ToolCallResult.success("done");
        });
        ToolCallExecutionService service = service(handler);
        ToolCallRequest request = request("call-4", "{}", 7L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ToolCallResult> first = pool.submit(() -> service.execute(request));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<ToolCallResult> duplicate = pool.submit(() -> service.execute(request));

            assertThat(duplicate.get(5, TimeUnit.SECONDS).errorCode()).isEqualTo("TOOL_CALL_IN_PROGRESS");
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo("SUCCESS");
            assertThat(((CountingHandler) handler).calls()).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void thrown_handler_exception_is_persisted_as_ambiguous() {
        AgentToolHandler handler = handler(true, request -> { throw new IllegalStateException("timeout"); });
        ToolCallExecutionService service = service(handler);

        ToolCallResult result = service.execute(request("call-5", "{}", 7L));

        assertThat(result.errorCode()).isEqualTo("TOOL_CALL_AMBIGUOUS");
        assertThat(executions.find(11L, "call-5").status()).isEqualTo(AgentToolCallExecutionStatus.AMBIGUOUS);
    }

    @Test
    void handler_cannot_return_gateway_governance_status() {
        AgentToolHandler handler = handler(false, request -> ToolCallResult.waitingApproval(99L));

        ToolCallResult result = service(handler).execute(request("call-invalid-status", "{}", 7L));

        assertThat(result.errorCode()).isEqualTo("TOOL_HANDLER_INVALID_RESULT");
        assertThat(executions.find(11L, "call-invalid-status").status())
                .isEqualTo(AgentToolCallExecutionStatus.FAILED);
    }

    @Test
    void mutation_guard_rejection_is_a_definitive_failure_before_handler_execution() {
        AgentToolHandler handler = mock(AgentToolHandler.class);
        when(handler.toolCode()).thenReturn("test_tool");
        when(handler.mutatesState(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        ToolCallRequest request = request("call-6", "{}", 7L);
        AuthorizedAgentRunContext context = context(7L);
        doThrow(new AgentToolMutationGuard.Rejection("AGENT_RUN_DEPENDENCY_CHANGED", "stale"))
                .when(guard).assertExecutable(context, true);

        ToolCallResult result = service(handler).execute(request);

        assertThat(result.errorCode()).isEqualTo("AGENT_RUN_DEPENDENCY_CHANGED");
        assertThat(executions.find(11L, "call-6").status()).isEqualTo(AgentToolCallExecutionStatus.FAILED);
        verify(handler, never()).execute(context, request);
    }

    @Test
    void supplied_context_must_match_run_and_execution_token_in_envelope() {
        AgentToolHandler handler = handler(false, request -> ToolCallResult.success("should-not-run"));
        ToolCallRequest request = request("call-context-mismatch", "{}", 7L);

        ToolCallResult result = service(handler).execute(context(8L), request);

        assertThat(result.errorCode()).isEqualTo("AGENT_RUN_EXECUTION_CONTEXT_MISMATCH");
        assertThat(((CountingHandler) handler).calls()).isZero();
        assertThat(executions.find(11L, "call-context-mismatch")).isNull();
    }

    @Test
    void disabled_tool_is_rejected_before_claiming_or_executing_the_handler() {
        AgentToolHandler handler = handler(false, request -> ToolCallResult.success("should-not-run"));
        ToolCallExecutionService service = service(handler, ToolLifecycleStatus.DISABLED);

        ToolCallResult result = service.execute(request("call-disabled", "{}", 7L));

        assertThat(result.errorCode()).isEqualTo("TOOL_DISABLED");
        assertThat(((CountingHandler) handler).calls()).isZero();
        assertThat(executions.find(11L, "call-disabled")).isNull();
    }

    @Test
    void draining_tool_remains_executable_for_existing_calls() {
        AgentToolHandler handler = handler(false, request -> ToolCallResult.success("drained"));

        ToolCallResult result = service(handler, ToolLifecycleStatus.DRAINING)
                .execute(request("call-draining", "{}", 7L));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput()).isEqualTo("drained");
        assertThat(((CountingHandler) handler).calls()).isEqualTo(1);
    }

    private ToolCallExecutionService service(AgentToolHandler handler) {
        return service(handler, ToolLifecycleStatus.ACTIVE);
    }

    private ToolCallExecutionService service(AgentToolHandler handler, ToolLifecycleStatus status) {
        when(executionContexts.resolve(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            ToolCallRequest request = invocation.getArgument(0);
            return context(request.executionToken());
        });
        return new ToolCallExecutionService(registry(handler, status), executions, ids, guard,
                executionContexts, new JacksonJsonCodec(objectMapper));
    }

    private AgentToolRegistry registry(AgentToolHandler handler) {
        return registry(handler, ToolLifecycleStatus.ACTIVE);
    }

    private AgentToolRegistry registry(AgentToolHandler handler, ToolLifecycleStatus status) {
        AgentToolDefinitionSource definitions = mock(AgentToolDefinitionSource.class);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                handler.toolCode(), new ToolPresentation("Test tool"),
                new ToolExposure(status, "test", "{\"type\":\"object\"}"),
                new ToolGovernancePolicy(new ApprovalPolicyDecision(false, ""), 0, Map.of()));
        when(definitions.listAll()).thenReturn(java.util.List.of(descriptor));
        return new AgentToolRegistry(definitions, java.util.List.of(handler),
                new NetworkntAgentToolSchemaValidator(objectMapper));
    }

    private AgentToolHandler handler(boolean mutates, ToolAction action) {
        return new CountingHandler(mutates, action);
    }

    private ToolCallRequest request(String callId, String args, Long token) {
        return new ToolCallRequest(11L, "test_tool", args, "idem", 1, callId,
                "[]", "[]", null, null, null, token);
    }

    private AuthorizedAgentRunContext context(Long token) {
        return AgentToolTestContext.context(1L, 11L, 2L, 3L, 4L, 9L, token, 5L, "trace");
    }

    private String hashFromFreshClaim(ToolCallRequest request) {
        InMemoryExecutions temporary = new InMemoryExecutions();
        ToolCallExecutionService hashingService = new ToolCallExecutionService(
                registry(handler(false, ignored -> ToolCallResult.success("ok"))), temporary,
                ids, guard, executionContexts, new JacksonJsonCodec(objectMapper));
        hashingService.execute(request);
        return temporary.find(request.runId(), request.toolCallId()).requestSha256();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("latch timeout");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", ex);
        }
    }

    @FunctionalInterface
    private interface ToolAction {
        ToolCallResult execute(ToolCallRequest request);
    }

    private static final class CountingHandler implements AgentToolHandler {
        private final boolean mutates;
        private final ToolAction action;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingHandler(boolean mutates, ToolAction action) {
            this.mutates = mutates;
            this.action = action;
        }

        @Override public String toolCode() { return "test_tool"; }
        @Override public boolean mutatesState(AuthorizedAgentRunContext context, ToolCallRequest request) {
            return mutates;
        }
        @Override public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
            calls.incrementAndGet();
            return action.execute(request);
        }
        int calls() { return calls.get(); }
    }

    private static final class InMemoryExecutions implements AgentToolCallExecutionRepository {
        private final Map<String, AgentToolCallExecution> values = new ConcurrentHashMap<>();

        @Override public AgentToolCallExecution find(Long runId, String toolCallId) {
            return values.get(key(runId, toolCallId));
        }

        @Override public boolean tryInsertStarted(AgentToolCallExecution execution) {
            return values.putIfAbsent(key(execution.runId(), execution.toolCallId()), execution) == null;
        }

        @Override
        public int markFinished(Long executionId, Long executionToken, AgentToolCallExecutionStatus status,
                                String resultJson, String errorCode, String errorMessage,
                                Instant finishedAt) {
            AtomicInteger changed = new AtomicInteger();
            values.computeIfPresent(findKey(executionId), (key, current) -> {
                if (!current.executionToken().equals(executionToken)
                        || current.status() != AgentToolCallExecutionStatus.STARTED) return current;
                changed.incrementAndGet();
                return new AgentToolCallExecution(current.executionId(), current.runId(), current.toolCallId(),
                        current.toolCode(), current.requestSha256(), current.executionToken(), status.name(),
                        resultJson, errorCode, errorMessage, current.startedAt(), finishedAt);
            });
            return changed.get();
        }

        void replaceStartedHash(Long runId, String toolCallId, String hash) {
            values.computeIfPresent(key(runId, toolCallId), (ignored, current) ->
                    AgentToolCallExecution.started(current.executionId(), current.runId(), current.toolCallId(),
                            current.toolCode(), hash, current.executionToken(), current.startedAt()));
        }

        private String findKey(Long executionId) {
            return values.entrySet().stream()
                    .filter(entry -> entry.getValue().executionId().equals(executionId))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse("missing:" + executionId);
        }

        private String key(Long runId, String toolCallId) { return runId + ":" + toolCallId; }
    }
}
