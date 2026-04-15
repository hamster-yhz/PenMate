package com.penmate.backend.application.agent.command;

public final class AgentCommands {

    private AgentCommands() {
    }

    /**
     * CreateConversationCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateConversationCommand(Long userId,
                                            String title,
                                            String contextScopeJson,
                                            String status,
                                            Long operatorId) {
    }

    /**
     * CreateMessageCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateMessageCommand(String role,
                                       String userMessageType,
                                       String contentMd,
                                       String attachmentsJson,
                                       String toolCallsJson,
                                       Long operatorId) {
    }

    /**
     * CreateGenerationCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateGenerationCommand(Long conversationId,
                                          Long chapterId,
                                          String taskType,
                                          String promptSnapshot,
                                          String styleProfileSnapshot,
                                          String pluginSnapshot,
                                          Long operatorId) {
    }

    /**
     * ApplyGenerationCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record ApplyGenerationCommand(Long operatorId,
                                         String applyNote) {
    }
}

