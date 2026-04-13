package com.penmate.backend.application.agent.command;

public final class AgentCommands {

    private AgentCommands() {
    }

    public record CreateConversationCommand(Long userId,
                                            String title,
                                            String contextScopeJson,
                                            String status,
                                            Long operatorId) {
    }

    public record CreateMessageCommand(String role,
                                       String userMessageType,
                                       String contentMd,
                                       String attachmentsJson,
                                       String toolCallsJson,
                                       Long operatorId) {
    }

    public record CreateGenerationCommand(Long conversationId,
                                          Long chapterId,
                                          String taskType,
                                          String promptSnapshot,
                                          String styleProfileSnapshot,
                                          String pluginSnapshot,
                                          Long operatorId) {
    }

    public record ApplyGenerationCommand(Long operatorId,
                                         String applyNote) {
    }
}

