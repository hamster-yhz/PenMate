package com.penmate.backend.application.agent.tool.catalog;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 静态 Agent tool 目录。
 * <p>该目录当前承担两类职责：</p>
 * <ol>
 *   <li>维护 {@code toolCode -> AgentToolDefinition} 元数据注册表，供审批与治理层查询；</li>
 *   <li>输出面向 LLM 的 {@link com.penmate.backend.application.agent.llm.AgentLlmToolSchema} 列表，决定哪些 tool 真正暴露给模型。</li>
 * </ol>
 * <p>因此，“已登记在目录中”与“已暴露给模型”并不是同一个概念：前者只表示应用层认识该 tool，后者还要求该 tool
 * 被纳入 {@link #toLlmToolSchemas()} 返回值。当前该返回值显式暴露 {@code context_enhancer} 与 {@code book_crud}。</p>
 */
@Component
@Slf4j
public class StaticAgentToolCatalog {

    private static final String CONTEXT_ENHANCER_PARAMETERS_JSON_SCHEMA = """
            {
              \"type\": \"object\",
              \"properties\": {
                \"prompt\": {
                  \"type\": \"string\"
                }
              },
              \"required\": [\"prompt\"]
            }
            """;

    /**
     * {@code book_crud} 采用单一 tool + operation 二级分发模式，因此暴露给 LLM 的 schema 需要同时描述
     * create/list/update/delete 四类操作，并通过条件分支把每个 operation 的必填字段约束暴露给模型。
     */
    private static final String BOOK_CRUD_PARAMETERS_JSON_SCHEMA = """
            {
              \"type\": \"object\",
              \"properties\": {
                \"operation\": {
                  \"type\": \"string\",
                  \"enum\": [\"create\", \"list\", \"update\", \"delete\"]
                },
                \"ownerUserId\": {
                  \"type\": \"integer\"
                },
                \"projectId\": {
                  \"type\": \"integer\"
                },
                \"title\": {
                  \"type\": \"string\"
                },
                \"summary\": {
                  \"type\": \"string\"
                },
                \"status\": {
                  \"type\": \"integer\"
                }
              },
              \"required\": [\"operation\"],
              \"oneOf\": [
                {
                  \"type\": \"object\",
                  \"properties\": {
                    \"operation\": {
                      \"const\": \"create\"
                    },
                    \"ownerUserId\": {
                      \"type\": \"integer\"
                    },
                    \"title\": {
                      \"type\": \"string\"
                    },
                    \"summary\": {
                      \"type\": \"string\"
                    },
                    \"status\": {
                      \"type\": \"integer\"
                    }
                  },
                  \"required\": [\"operation\", \"ownerUserId\", \"title\"],
                  \"additionalProperties\": false
                },
                {
                  \"type\": \"object\",
                  \"properties\": {
                    \"operation\": {
                      \"const\": \"list\"
                    }
                  },
                  \"required\": [\"operation\"],
                  \"additionalProperties\": false
                },
                {
                  \"type\": \"object\",
                  \"properties\": {
                    \"operation\": {
                      \"const\": \"update\"
                    },
                    \"projectId\": {
                      \"type\": \"integer\"
                    },
                    \"title\": {
                      \"type\": \"string\"
                    },
                    \"summary\": {
                      \"type\": \"string\"
                    },
                    \"status\": {
                      \"type\": \"integer\"
                    }
                  },
                  \"required\": [\"operation\", \"projectId\"],
                  \"additionalProperties\": false
                },
                {
                  \"type\": \"object\",
                  \"properties\": {
                    \"operation\": {
                      \"const\": \"delete\"
                    },
                    \"projectId\": {
                      \"type\": \"integer\"
                    }
                  },
                  \"required\": [\"operation\", \"projectId\"],
                  \"additionalProperties\": false
                }
              ]
            }
            """;

    private final Map<String, AgentToolDefinition> registry = Map.of(
            "context_enhancer", new AgentToolDefinition("context_enhancer", "上下文增强", false, "", 1),
            "book_crud", new AgentToolDefinition("book_crud", "书籍 CRUD", false, "BOOK_CRUD", 2)
    );

    public AgentToolDefinition getRequired(String toolCode) {
        AgentToolDefinition metadata = registry.get(toolCode);
        if (metadata == null) {
            log.warn("读取 tool 元数据失败: toolCode={}, reason=not_found", toolCode);
            throw new IllegalArgumentException("Tool metadata not found: " + toolCode);
        }
        log.debug("读取 tool 元数据成功: toolCode={}, displayName={}, approvalRequired={}",
                metadata.toolCode(), metadata.displayName(), metadata.approvalRequired());
        return metadata;
    }

    public List<AgentLlmToolSchema> toLlmToolSchemas() {
        return List.of(
                new AgentLlmToolSchema(
                        "context_enhancer",
                        "补充上下文",
                        CONTEXT_ENHANCER_PARAMETERS_JSON_SCHEMA
                ),
                new AgentLlmToolSchema(
                        "book_crud",
                        "书籍 CRUD；必须提供 operation，并按 create/list/update/delete 传入对应字段",
                        BOOK_CRUD_PARAMETERS_JSON_SCHEMA
                )
        );
    }
}
