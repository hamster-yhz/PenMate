package com.penmate.backend.application.agent.llm;

import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;

import java.util.List;

/**
 * Agent 大模型调用网关抽象。
 * <p>应用层通过该接口屏蔽底层模型 SDK 差异，统一完成提示词输入、RAG 片段注入与生成结果输出。</p>
 * <p>实现方需保证：在可用配置缺失时快速失败，避免静默回退到环境默认配置。</p>
 */
public interface AgentLlmGateway {

    /**
     * 执行一次结构化 turn 生成。
     */
    AgentLlmTurnResponse generateTurn(AgentLlmTurnRequest request,
                                      AgentLlmExecutionConfig executionConfig);

    /**
     * 执行一次文本生成。
     * <p>输入由任务快照、知识库检索片段、工具增强上下文和执行配置组成；输出为可持久化的最终文本。</p>
     *
     * @param task 当前生成任务快照，包含用户提示词、风格快照等核心上下文
     * @param ragChunks 检索到的知识片段列表，可为空
     * @param toolContext 工具执行聚合结果文本，可为空
     * @param executionConfig 路由后的模型执行配置；不能为空
     * @return 模型生成的正文文本
     */
    String generate(AgentGenerationTask task,
                    List<RagRetrievedChunk> ragChunks,
                    String toolContext,
                    AgentLlmExecutionConfig executionConfig);
}

