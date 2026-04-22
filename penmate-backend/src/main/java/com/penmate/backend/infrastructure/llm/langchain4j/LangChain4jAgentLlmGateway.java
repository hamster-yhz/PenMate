package com.penmate.backend.infrastructure.llm.langchain4j;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.infrastructure.llm.langchain4j.provider.ProviderChatClient;
import com.penmate.backend.infrastructure.llm.langchain4j.provider.ProviderChatClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 LangChain4j 的 Agent 模型网关实现。
 * <p>该实现负责把任务快照、RAG 片段与工具上下文组装为统一提示词，并按路由配置调用 OpenAI 兼容接口。</p>
 * <p>当模型配置不可用或启用 mock 时，直接抛出业务异常，阻断生成链路。</p>
 */
@Component
@Slf4j
public class LangChain4jAgentLlmGateway implements AgentLlmGateway {

    private final ProviderChatClientFactory providerChatClientFactory;

    public LangChain4jAgentLlmGateway(ProviderChatClientFactory providerChatClientFactory) {
        this.providerChatClientFactory = providerChatClientFactory;
    }

    /**
     * 调用大模型生成文本。
     * <p><b>业务目的：</b>为 Agent 生成任务产出可持久化正文；配置不合法时快速失败并抛业务异常。</p>
     * <p><b>流程主线：</b></p>
     * <ol>
     *   <li>严格校验执行配置（必须携带 provider/baseUrl/apiKey/modelName）。</li>
     *   <li>按 provider 选择具体调用策略实现。</li>
     *   <li>拼装完整 prompt，并交由对应 provider client 执行调用。</li>
     * </ol>
     * <p><b>关键调用：</b>{@link ProviderChatClientFactory#get(String)} 负责策略分发；{@link #buildPrompt(AgentGenerationTask, List, String)} 负责上下文拼装。</p>
     * <p><b>异常与分支：</b>配置不满足时抛出 {@link BusinessException}。</p>
     * <p><b>副作用：</b>无数据库写入；仅执行外部模型调用。</p>
     *
     * @param task 生成任务快照，包含用户提示词与风格快照
     * @param ragChunks 检索知识片段，可为空
     * @param toolContext 工具增强结果，可为空
     * @param executionConfig 模型路由配置，不可为空
     * @return 模型生成文本
     */
    @Override
    public String generate(AgentGenerationTask task,
                           List<RagRetrievedChunk> ragChunks,
                           String toolContext,
                           AgentLlmExecutionConfig executionConfig) {
        if (executionConfig == null) {
            throw BusinessException.of("LLM execution config is required");
        }
        String provider = executionConfig.providerCode();
        String baseUrl = executionConfig.baseUrl();
        String apiKey = executionConfig.apiKey();
        String modelName = executionConfig.modelName();
        if (provider == null || provider.isBlank() || baseUrl == null || baseUrl.isBlank()
                || apiKey == null || apiKey.isBlank() || modelName == null || modelName.isBlank()) {
            throw BusinessException.of("LLM execution config is incomplete");
        }

        log.info("agent.llm.gateway.resolved: taskId={}, modelConfigId={}, keySource={}, provider={}, baseUrl={}, modelName={}",
                task == null ? null : task.getId(),
                executionConfig.modelConfigId(),
                executionConfig.keySource(),
                provider,
                baseUrl,
                modelName);

        String prompt = buildPrompt(task, ragChunks, toolContext);
        ProviderChatClient providerChatClient = providerChatClientFactory.get(provider);
        return providerChatClient.generate(prompt, executionConfig);
    }

    /**
     * 组装模型提示词。
     * <p>按“风格约束 -> 知识库参考 -> 工具增强结果 -> 用户指令”顺序拼接，确保模型先读取约束与上下文再处理需求。</p>
     */
    private String buildPrompt(AgentGenerationTask task, List<RagRetrievedChunk> ragChunks, String toolContext) {
        String prompt = task.getPromptSnapshot() == null ? "" : task.getPromptSnapshot().trim();
        String style = task.getStyleProfileSnapshot() == null ? "" : task.getStyleProfileSnapshot().trim();
        StringBuilder builder = new StringBuilder();

        if (!style.isEmpty()) {
            builder.append("写作风格约束：\n").append(style).append("\n\n");
        }
        if (ragChunks != null && !ragChunks.isEmpty()) {
            builder.append("知识库参考：\n");
            for (RagRetrievedChunk chunk : ragChunks) {
                builder.append("- [")
                        .append(chunk.getDocumentTitle() == null ? "文档" : chunk.getDocumentTitle())
                        .append("#")
                        .append(chunk.getChunkNo() == null ? 0 : chunk.getChunkNo())
                        .append("] ")
                        .append(chunk.getContentText() == null ? "" : chunk.getContentText())
                        .append("\n");
            }
            builder.append("\n");
        }
        if (toolContext != null && !toolContext.isBlank()) {
            builder.append("工具增强结果：\n").append(toolContext).append("\n\n");
        }
        builder.append("用户指令：\n").append(prompt);
        return builder.toString();
    }

}
