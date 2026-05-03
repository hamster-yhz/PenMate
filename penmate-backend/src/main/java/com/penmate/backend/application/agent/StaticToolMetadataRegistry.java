package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class StaticToolMetadataRegistry {

    private static final String CONTEXT_ENHANCER_PARAMETERS_JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "prompt": {
                  "type": "string"
                }
              },
              "required": ["prompt"]
            }
            """;

    private final Map<String, ToolMetadata> registry = Map.of(
            "context_enhancer", new ToolMetadata("context_enhancer", "上下文增强", false, "", 1),
            "book_crud", new ToolMetadata("book_crud", "书籍 CRUD", false, "BOOK_CRUD", 2)
    );

    public ToolMetadata getRequired(String toolCode) {
        ToolMetadata metadata = registry.get(toolCode);
        if (metadata == null) {
            log.warn("读取 tool 元数据失败: toolCode={}, reason=not_found", toolCode);
            throw new IllegalArgumentException("Tool metadata not found: " + toolCode);
        }
        log.debug("读取 tool 元数据成功: toolCode={}, displayName={}, approvalRequired={}",
                metadata.toolCode(), metadata.displayName(), metadata.approvalRequired());
        return metadata;
    }

    public List<AgentLlmToolSchema> toLlmToolSchemas() {
        return List.of(new AgentLlmToolSchema(
                "context_enhancer",
                "补充上下文",
                CONTEXT_ENHANCER_PARAMETERS_JSON_SCHEMA
        ));
    }
}
