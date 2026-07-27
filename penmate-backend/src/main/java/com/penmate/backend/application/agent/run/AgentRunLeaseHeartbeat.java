package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AgentRunLeaseHeartbeat {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMinutes(1);
    private final AgentRunLeaseService leaseService;
    private final ScheduledExecutorService scheduler;
    private final Duration heartbeatInterval;
    private final ConcurrentMap<LeaseKey, AtomicReference<RuntimeException>> active = new ConcurrentHashMap<>();

    @Autowired
    public AgentRunLeaseHeartbeat(AgentRunLeaseService leaseService) {
        this(leaseService, Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "agent-run-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        }), HEARTBEAT_INTERVAL);
    }

    AgentRunLeaseHeartbeat(AgentRunLeaseService leaseService,
                           ScheduledExecutorService scheduler,
                           Duration heartbeatInterval) {
        this.leaseService = leaseService;
        this.scheduler = scheduler;
        this.heartbeatInterval = heartbeatInterval;
    }

    public Registration start(AgentRunLease lease) {
        if (!leaseService.renew(lease)) {
            throw new AgentRunLeaseService.AgentRunLeaseLostException(
                    lease.runId(), lease.executionToken());
        }
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        LeaseKey key = new LeaseKey(lease.runId(), lease.executionToken());
        if (active.putIfAbsent(key, failure) != null) {
            throw new IllegalStateException("Agent Run lease heartbeat is already active: runId="
                    + lease.runId() + ", executionToken=" + lease.executionToken());
        }
        ScheduledFuture<?> future;
        try {
            future = scheduler.scheduleAtFixedRate(() -> {
                try {
                    if (!leaseService.renew(lease)) {
                        failure.compareAndSet(null, new AgentRunLeaseService.AgentRunLeaseLostException(
                                lease.runId(), lease.executionToken()));
                    }
                } catch (RuntimeException ex) {
                    failure.compareAndSet(null, ex);
                }
            }, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException ex) {
            active.remove(key, failure);
            throw ex;
        }
        return new Registration(key, future, failure);
    }

    public void assertHealthy(Long runId, Long executionToken) {
        AtomicReference<RuntimeException> failure = active.get(new LeaseKey(runId, executionToken));
        if (failure == null) return;
        RuntimeException exception = failure.get();
        if (exception != null) throw exception;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    public final class Registration implements AutoCloseable {
        private final LeaseKey key;
        private final ScheduledFuture<?> future;
        private final AtomicReference<RuntimeException> failure;

        private Registration(LeaseKey key, ScheduledFuture<?> future,
                             AtomicReference<RuntimeException> failure) {
            this.key = key;
            this.future = future;
            this.failure = failure;
        }

        public void assertHealthy() {
            RuntimeException exception = failure.get();
            if (exception != null) throw exception;
        }

        @Override
        public void close() {
            future.cancel(false);
            active.remove(key, failure);
        }
    }

    private record LeaseKey(Long runId, Long executionToken) { }
}
