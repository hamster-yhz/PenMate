package com.penmate.backend.application.agent.llm;

public interface AgentLlmStreamObserver {

    void onResponseStarted();

    void onTextDelta(String text);

    default void onEvent(AgentLlmStreamEvent event) {
        if (event instanceof AgentLlmStreamEvent.OutputTextDelta delta) {
            onTextDelta(delta.text());
        }
    }

    void onCancellable(Runnable cancelAction);

    boolean isCancelled();
}
