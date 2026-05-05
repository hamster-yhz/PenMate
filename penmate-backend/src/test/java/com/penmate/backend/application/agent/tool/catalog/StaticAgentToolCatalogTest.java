package com.penmate.backend.application.agent.tool.catalog;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StaticAgentToolCatalogTest {

    @Test
    void UT_APP_AGENT_TOOL_CATALOG_TO_LLM_TOOL_SCHEMAS_SHOULD_EXPOSE_BOOK_CRUD_SCHEMA() {
        StaticAgentToolCatalog catalog = new StaticAgentToolCatalog();

        Map<String, AgentLlmToolSchema> schemasByToolCode = catalog.toLlmToolSchemas().stream()
                .collect(Collectors.toMap(AgentLlmToolSchema::toolCode, schema -> schema));

        assertThat(schemasByToolCode).containsKey("book_crud");
        assertThat(schemasByToolCode.get("book_crud").description())
                .contains("书籍 CRUD")
                .contains("operation");

        JSONObject schema = AgentJsonCodec.parseObj(schemasByToolCode.get("book_crud").parametersJsonSchema());
        JSONArray oneOf = schema.getJSONArray("oneOf");
        JSONObject createBranch = oneOf.getJSONObject(0);
        JSONObject listBranch = oneOf.getJSONObject(1);
        JSONObject updateBranch = oneOf.getJSONObject(2);
        JSONObject deleteBranch = oneOf.getJSONObject(3);

        assertThat(schema.getJSONObject("properties").containsKey("ownerUserId")).isTrue();
        assertThat(schema.getJSONObject("properties").containsKey("projectId")).isTrue();
        assertThat(oneOf).hasSize(4);

        assertThat(createBranch.getJSONObject("properties").getJSONObject("operation").getStr("const")).isEqualTo("create");
        assertThat(createBranch.getJSONArray("required").toList(String.class)).containsExactly("operation", "ownerUserId", "title");
        assertThat(createBranch.getBool("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat(createBranch.getJSONObject("properties").keySet())
                .containsExactlyInAnyOrder("operation", "ownerUserId", "title", "summary", "status");

        assertThat(listBranch.getJSONObject("properties").getJSONObject("operation").getStr("const")).isEqualTo("list");
        assertThat(listBranch.getJSONArray("required").toList(String.class)).containsExactly("operation");
        assertThat(listBranch.getBool("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat(listBranch.getJSONObject("properties").keySet()).containsExactly("operation");

        assertThat(updateBranch.getJSONObject("properties").getJSONObject("operation").getStr("const")).isEqualTo("update");
        assertThat(updateBranch.getJSONArray("required").toList(String.class)).containsExactly("operation", "projectId");
        assertThat(updateBranch.getBool("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat(updateBranch.getJSONObject("properties").keySet())
                .containsExactlyInAnyOrder("operation", "projectId", "title", "summary", "status");

        assertThat(deleteBranch.getJSONObject("properties").getJSONObject("operation").getStr("const")).isEqualTo("delete");
        assertThat(deleteBranch.getJSONArray("required").toList(String.class)).containsExactly("operation", "projectId");
        assertThat(deleteBranch.getBool("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat(deleteBranch.getJSONObject("properties").keySet())
                .containsExactlyInAnyOrder("operation", "projectId");
    }
}
