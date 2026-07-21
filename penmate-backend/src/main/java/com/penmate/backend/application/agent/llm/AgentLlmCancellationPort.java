package com.penmate.backend.application.agent.llm;

public interface AgentLlmCancellationPort {

    Registration register(Long runId, Runnable cancelAction);

    void cancel(Long runId);

    interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
