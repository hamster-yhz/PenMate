package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.rag.RagRetrievalService;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 查询 tool 处理器。
 */
@Component
public class RagQueryToolHandler implements AgentToolHandler {

    private final RagRetrievalService ragRetrievalService;

    public RagQueryToolHandler(RagRetrievalService ragRetrievalService) {
        this.ragRetrievalService = ragRetrievalService;
    }

    @Override
    public String toolCode() {
        return "rag_query";
    }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        JSONObject args = AgentJsonCodec.parseObj(request.toolArgsJson());
        String query = AgentJsonCodec.getString(args, "query");
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        try {
            JSONObject args = AgentJsonCodec.parseObj(request.toolArgsJson());
            String query = AgentJsonCodec.getString(args, "query");
            List<RagRetrievedChunk> chunks = ragRetrievalService.retrieve(
                    request.projectId(),
                    request.taskId(),
                    query,
                    request.traceId()
            ).chunks();
            return ToolCallResult.success(formatChunks(chunks));
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "rag query execution failed"
                    : ex.getMessage();
            return new ToolCallResult("FAILED", null, null, "RAG_QUERY_FAILED", errorMessage);
        }
    }

    private String formatChunks(List<RagRetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "未检索到可用知识库片段。";
        }
        StringBuilder builder = new StringBuilder("知识库检索结果：\n");
        for (RagRetrievedChunk chunk : chunks) {
            builder.append("- [")
                    .append(chunk.getDocumentTitle() == null ? "文档" : chunk.getDocumentTitle())
                    .append("#")
                    .append(chunk.getChunkNo() == null ? 0 : chunk.getChunkNo())
                    .append("] ")
                    .append(chunk.getContentText() == null ? "" : chunk.getContentText())
                    .append("\n");
        }
        return builder.toString().trim();
    }
}
