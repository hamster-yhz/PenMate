package com.penmate.backend.infrastructure.agent.run;

import com.penmate.backend.application.agent.run.AgentRunDispatcher;
import com.penmate.backend.application.agent.run.AgentRunDispatchRequested;
import com.penmate.backend.application.agent.run.AgentRunInitialExecutionService;
import com.penmate.backend.application.agent.run.AgentRunRecoveryExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentRunDispatchEventAdapterTest {

    @Test
    void publishes_application_request_through_spring_event_bus() {
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        AgentRunDispatchRequested request = new AgentRunDispatchRequested(42L, "trace-42");

        new SpringAgentRunDispatchRequestPublisher(events).publish(request);

        verify(events).publishEvent(request);
    }

    @Test
    void dispatches_after_commit_and_keeps_non_transactional_fallback() throws Exception {
        AgentRunDispatcher dispatcher = mock(AgentRunDispatcher.class);
        AgentRunDispatchRequestedListener listener = new AgentRunDispatchRequestedListener(dispatcher);

        listener.on(new AgentRunDispatchRequested(42L, "trace-42"));

        verify(dispatcher).dispatchInitialRun(42L, "trace-42");
        Method method = AgentRunDispatchRequestedListener.class.getMethod("on", AgentRunDispatchRequested.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
    }

    @Test
    void async_dispatchers_delegate_to_synchronous_application_use_cases() throws Exception {
        AgentRunInitialExecutionService initial = mock(AgentRunInitialExecutionService.class);
        AgentRunRecoveryExecutionService recovery = mock(AgentRunRecoveryExecutionService.class);
        AsyncAgentRunDispatcher initialDispatcher = new AsyncAgentRunDispatcher(initial);
        AsyncAgentRunResumeDispatcher recoveryDispatcher = new AsyncAgentRunResumeDispatcher(recovery);

        initialDispatcher.dispatchInitialRun(41L, "trace-41");
        recoveryDispatcher.dispatchResume(42L, "trace-42");
        recoveryDispatcher.dispatchRecovery(43L, "trace-43");

        verify(initial).execute(41L, "trace-41");
        verify(recovery).execute(42L, "trace-42");
        verify(recovery).execute(43L, "trace-43");
        assertThat(AsyncAgentRunDispatcher.class
                .getMethod("dispatchInitialRun", Long.class, String.class)
                .isAnnotationPresent(Async.class)).isTrue();
        assertThat(AsyncAgentRunResumeDispatcher.class
                .getMethod("dispatchResume", Long.class, String.class)
                .isAnnotationPresent(Async.class)).isTrue();
        assertThat(AsyncAgentRunResumeDispatcher.class
                .getMethod("dispatchRecovery", Long.class, String.class)
                .isAnnotationPresent(Async.class)).isTrue();
    }
}
