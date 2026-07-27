package com.penmate.backend.application.agent.command;

/**
 * Agent 应用层命令集合。
 * <p>这些 record 用于承接接口层输入并向应用服务提供稳定入参，避免 Controller 直接依赖领域对象或持久化结构。</p>
 */
public final class AgentCommands {

    private AgentCommands() {
    }

    /**
     * 创建会话命令。
     * <p>描述创建会话所需的业务输入快照，本身不承载任何流程逻辑。</p>
     */
    public record CreateConversationCommand(Long userId,
                                            String title,
                                            String status,
                                            Long operatorId) {
    }

    /**
     * 创建消息命令。
     * <p>封装一次消息写入所需字段，包括角色、正文、附件与 tool call 回写载荷。</p>
     */
    public record CreateMessageCommand(String role,
                                       String userMessageType,
                                       String contentMd,
                                       String attachmentsJson,
                                       String toolCallsJson,
                                       Long operatorId) {
    }

    /**
     * 创建生成任务命令。
     * <p>封装一次 agent 生成任务的静态输入，包括会话、模型配置、prompt 与插件快照。</p>
     */
    public record CreateGenerationCommand(Long conversationId,
                                          Long chapterId,
                                          Long modelConfigId,
                                          String promptSnapshot,
                                          String pluginSnapshot,
                                          Long operatorId) {
    }

    /**
     * 应用生成结果命令。
     * <p>用于表达“人工确认采用本次生成结果”这一用例输入。</p>
     */
    public record ApplyGenerationCommand(Long operatorId,
                                         String applyNote) {
    }
}
