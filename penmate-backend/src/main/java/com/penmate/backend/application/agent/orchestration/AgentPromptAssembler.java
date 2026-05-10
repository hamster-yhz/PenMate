package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Agent 初始提示词装配器。
 * <p>负责把系统提示词、任务 prompt、任务上下文中的风格快照与 RAG 检索片段拼装成首轮发送给 LLM 的消息列表。</p>
 */
@Component
public class AgentPromptAssembler {

    private final SystemPromptProvider systemPromptProvider;

    public AgentPromptAssembler(SystemPromptProvider systemPromptProvider) {
        this.systemPromptProvider = systemPromptProvider;
    }

    public List<Map<String, Object>> buildInitialMessages(AgentGenerationTask task,
                                                          AgentTaskContext taskContext,
                                                          List<RagRetrievedChunk> ragChunks) {
        return buildExecutionMessages(task, taskContext, ragChunks, resolveProfile(task), "");
    }

    public List<Map<String, Object>> buildExecutionMessages(AgentGenerationTask task,
                                                            AgentTaskContext taskContext,
                                                            List<RagRetrievedChunk> ragChunks,
                                                            String executionProfile,
                                                            String storyBibleContent) {
        String prompt = task.getPromptSnapshot() == null ? "" : task.getPromptSnapshot().trim();
        String style = taskContext == null || taskContext.getStyleSnapshotJson() == null
                ? ""
                : taskContext.getStyleSnapshotJson().trim();
        String storyBible = storyBibleContent == null ? "" : storyBibleContent.trim();
        StringJoiner userBuilder = new StringJoiner("\n\n");

        if (!style.isEmpty()) {
            userBuilder.add(wrapBlock("context type=\"style\"", style));
        }
        if (ragChunks != null && !ragChunks.isEmpty()) {
            StringBuilder ragBuilder = new StringBuilder();
            for (RagRetrievedChunk chunk : ragChunks) {
                ragBuilder.append("- [")
                        .append(chunk.getDocumentTitle() == null ? "文档" : chunk.getDocumentTitle())
                        .append("#")
                        .append(chunk.getChunkNo() == null ? 0 : chunk.getChunkNo())
                        .append("] ")
                        .append(chunk.getContentText() == null ? "" : chunk.getContentText())
                        .append("\n");
            }
            userBuilder.add(wrapBlock("context type=\"rag\"", ragBuilder.toString()));
        }
        if (!storyBible.isEmpty()) {
            userBuilder.add(wrapBlock("context type=\"story_bible\"", storyBible));
        }
        userBuilder.add(wrapBlock("user_request", prompt));

        String profile = executionProfile == null || executionProfile.isBlank() ? resolveProfile(task) : executionProfile.trim();
        SystemPromptBundle promptBundle = systemPromptProvider.loadBundle("execution", profile);
        return List.of(
                Map.of(
                        "role", "system",
                        "content", promptBundle.assembledPrompt()
                ),
                Map.of(
                        "role", "user",
                        "content", userBuilder.toString()
                )
        );
    }

    private String wrapBlock(String tagDeclaration, String content) {
        return "<" + tagDeclaration + ">\n" + normalizeBlockContent(content) + "\n</" + closingTagName(tagDeclaration) + ">";
    }

    private String normalizeBlockContent(String content) {
        if (content == null) {
            return "";
        }
        return escapeStructuredContent(content
                .replaceFirst("^[\\r\\n]+", "")
                .replaceFirst("[\\r\\n]+$", ""));
    }

    private String escapeStructuredContent(String content) {
        return content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String closingTagName(String tagDeclaration) {
        int separatorIndex = tagDeclaration.indexOf(' ');
        return separatorIndex < 0 ? tagDeclaration : tagDeclaration.substring(0, separatorIndex);
    }

    private String resolveProfile(AgentGenerationTask task) {
        if (task == null || task.getTaskType() == null) {
            return "default";
        }
        return switch (task.getTaskType().trim().toUpperCase()) {
            case "WORLD_BUILD" -> "world-build";
            case "REWRITE" -> "rewrite";
            default -> "default";
        };
    }
}
