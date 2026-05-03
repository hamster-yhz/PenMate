package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AgentPromptAssembler {

    public List<Map<String, Object>> buildInitialMessages(AgentGenerationTask task, List<RagRetrievedChunk> ragChunks) {
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
        builder.append("用户指令：\n").append(prompt);
        return List.of(Map.of(
                "role", "user",
                "content", builder.toString()
        ));
    }
}
