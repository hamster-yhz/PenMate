package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.rag.RagRetrievalService;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagQueryToolHandlerTest {

    private final RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
    private final RagQueryToolHandler handler = new RagQueryToolHandler(
            ragRetrievalService, new JacksonJsonCodec(new ObjectMapper()));

    @Test
    void UT_APP_AGENT_RAG_QUERY_TOOL_HANDLER_VALIDATE_SHOULD_REJECT_BLANK_QUERY() {
        ToolCallRequest request = request("""
                {
                  "query": "   "
                }
                """);

        assertThatThrownBy(() -> handler.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query must not be blank");
    }

    @Test
    void UT_APP_AGENT_RAG_QUERY_TOOL_HANDLER_EXECUTE_SHOULD_RETURN_FORMATTED_RETRIEVAL_OUTPUT() {
        RagRetrievedChunk chunk = new RagRetrievedChunk();
        chunk.setDocumentTitle("设定集");
        chunk.setChunkNo(2);
        chunk.setContentText("王都位于北境河谷。");
        when(ragRetrievalService.retrieve(9001L, 8001L, "王都位置", "trace-1"))
                .thenReturn(new RagRetrievalService.RetrievalResult(List.of(chunk), 6001L));

        ToolCallResult result = handler.execute(request("""
                {
                  "query": "王都位置"
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput()).contains("知识库检索结果：");
        assertThat(result.toolOutput()).contains("[设定集#2] 王都位于北境河谷。");
        verify(ragRetrievalService).retrieve(9001L, 8001L, "王都位置", "trace-1");
    }

    @Test
    void UT_APP_AGENT_RAG_QUERY_TOOL_HANDLER_EXECUTE_SHOULD_RETURN_EMPTY_HINT_WHEN_NO_CHUNK_FOUND() {
        when(ragRetrievalService.retrieve(9001L, 8001L, "冷门设定", "trace-1"))
                .thenReturn(new RagRetrievalService.RetrievalResult(List.of(), 6002L));

        ToolCallResult result = handler.execute(request("""
                {
                  "query": "冷门设定"
                }
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput()).isEqualTo("未检索到可用知识库片段。");
    }

    @Test
    void UT_APP_AGENT_RAG_QUERY_TOOL_HANDLER_EXECUTE_SHOULD_MAP_EXCEPTION_TO_STABLE_FAILED_RESULT() {
        when(ragRetrievalService.retrieve(9001L, 8001L, "异常查询", "trace-1"))
                .thenThrow(new RuntimeException());

        ToolCallResult result = handler.execute(request("""
                {
                  "query": "异常查询"
                }
                """));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("RAG_QUERY_FAILED");
        assertThat(result.errorMessage()).isEqualTo("rag query execution failed");
    }

    private ToolCallRequest request(String toolArgsJson) {
        return new ToolCallRequest(
                9001L,
                8001L,
                7001L,
                "rag_query",
                toolArgsJson,
                1001L,
                "trace-1",
                "{}",
                "idem-rag-1"
        );
    }
}
