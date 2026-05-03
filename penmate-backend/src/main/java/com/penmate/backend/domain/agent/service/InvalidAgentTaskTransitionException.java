package com.penmate.backend.domain.agent.service;

public class InvalidAgentTaskTransitionException extends RuntimeException {

    public InvalidAgentTaskTransitionException(String message) {
        super(message);
    }
}
