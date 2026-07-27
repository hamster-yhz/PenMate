package com.penmate.backend.application.agent.context;

public enum StoryBibleRoutingMode {
    AGENT_DRIVEN,
    RETRIEVAL,
    LLM_SELECTOR,
    RETRIEVAL_THEN_LLM;

    public boolean preparesContext() {
        return this != AGENT_DRIVEN;
    }

    public boolean usesSelector() {
        return this == LLM_SELECTOR || this == RETRIEVAL_THEN_LLM;
    }

    public boolean usesRetrieval() {
        return this == RETRIEVAL || this == RETRIEVAL_THEN_LLM;
    }
}
