package com.penmate.backend.application.agent.llm;

public interface AgentLlmStreamObserver {

    void onResponseStarted();

    void onTextDelta(String text);

    void onCancellable(Runnable cancelAction);

    boolean isCancelled();
}
