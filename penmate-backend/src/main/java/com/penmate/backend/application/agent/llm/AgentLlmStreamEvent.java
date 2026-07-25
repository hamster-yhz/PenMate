package com.penmate.backend.application.agent.llm;

public sealed interface AgentLlmStreamEvent {

    record OutputTextDelta(String text) implements AgentLlmStreamEvent {}

    record CommentaryDelta(String text) implements AgentLlmStreamEvent {}

    record ReasoningSummaryDelta(String text) implements AgentLlmStreamEvent {}
}
