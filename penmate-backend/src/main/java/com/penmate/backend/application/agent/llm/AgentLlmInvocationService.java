package com.penmate.backend.application.agent.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
@Slf4j
public class AgentLlmInvocationService {

    private final AgentLlmGateway gateway;
    private final AgentLlmCancellationPort cancellations;

    @Autowired
    public AgentLlmInvocationService(AgentLlmGateway gateway,
                                     AgentLlmCancellationPort cancellations) {
        this.gateway = gateway;
        this.cancellations = cancellations;
    }

    public AgentLlmInvocationService(AgentLlmGateway gateway) {
        this(gateway, new AgentLlmCancellationPort() {
            @Override
            public Registration register(Long runId, Runnable cancelAction) {
                return () -> { };
            }

            @Override
            public void cancel(Long runId) {
                // Buffered compatibility calls do not expose an upstream handle.
            }
        });
    }

    public AgentLlmTurnResponse invokeBuffered(AgentLlmTurnRequest request,
                                               AgentLlmExecutionConfig executionConfig) {
        return gateway.generateTurn(request, executionConfig);
    }

    public AgentLlmTurnResponse invokeStreaming(Long runId,
                                                AgentLlmTurnRequest request,
                                                AgentLlmExecutionConfig executionConfig,
                                                Consumer<String> onTextDelta) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(onTextDelta, "onTextDelta must not be null");
        if (!gateway.supportsStreaming(executionConfig)) {
            return gateway.generateTurn(request, executionConfig);
        }

        AtomicBoolean responseStarted = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Runnable> upstreamCancel = new AtomicReference<>(() -> { });
        try (AgentLlmCancellationPort.Registration ignored = cancellations.register(runId, () -> {
            cancelled.set(true);
            upstreamCancel.get().run();
        })) {
            AgentLlmStreamObserver observer = new AgentLlmStreamObserver() {
                @Override
                public void onResponseStarted() {
                    responseStarted.set(true);
                }

                @Override
                public void onTextDelta(String text) {
                    if (isCancelled()) throw new AgentLlmInvocationCancelledException();
                    if (text != null && !text.isEmpty()) onTextDelta.accept(text);
                }

                @Override
                public void onCancellable(Runnable cancelAction) {
                    upstreamCancel.set(cancelAction == null ? () -> { } : cancelAction);
                    if (cancelled.get()) upstreamCancel.get().run();
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get();
                }
            };
            try {
                return gateway.streamTurn(request, executionConfig, observer);
            } catch (AgentLlmInvocationCancelledException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                if (responseStarted.get()) throw ex;
                log.warn("agent.llm.stream.fallback: runId={}, provider={}, reason={}",
                        runId, executionConfig == null ? null : executionConfig.providerCode(), ex.getMessage());
                return gateway.generateTurn(request, executionConfig);
            }
        }
    }
}
