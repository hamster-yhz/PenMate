package com.penmate.backend.interfaces.api.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.rag.RagApplicationService;
import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RagControllerTest {

    @Mock
    private RagApplicationService ragApplicationService;

    @InjectMocks
    private RagController ragController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(ragController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    // 创建文档成功。
    void UT_RAG_DOCUMENT_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RAG-DOC-CREATE";
        RagDocument doc = new RagDocument();
        doc.setId(9001L);
        doc.setDocType("design");
        doc.setTitle("世界观设定");

        when(ragApplicationService.createDocument(eq(10001L), any(), eq(traceId))).thenReturn(doc);

        mockMvc().perform(post("/api/v1/novels/10001/rag/documents")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "docType", "design",
                                "title", "世界观设定",
                                "originObjectKey", "novels/10001/rag/obj-1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9001))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 上传URL获取成功。
    void UT_RAG_UPLOAD_URL_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RAG-UPLOAD-URL";
        when(ragApplicationService.getDocumentUploadUrl(10001L)).thenReturn(Map.of(
                "uploadUrl", "https://oss.example/upload",
                "objectKey", "novels/10001/rag/obj-1"
        ));

        mockMvc().perform(post("/api/v1/novels/10001/rag/documents/upload-url")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").exists())
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 向量化异常映射。
    void UT_RAG_EMBED_ERROR_CODE_MAPPING() throws Exception {
        String traceId = "UT-TRACE-RAG-EMBED-ERROR";
        doThrow(new IllegalArgumentException("Embedding provider timeout"))
                .when(ragApplicationService).embedDocument(eq(10001L), eq(9001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/rag/documents/9001/embed")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))      
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 检索日志分页接口基本成功（返回列表）。
    void UT_RAG_RETRIEVAL_LOGS_PAGINATION_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RAG-LOGS";
        when(ragApplicationService.listRetrievalLogs(10001L)).thenReturn(List.of(Map.of(
                "query", "主角设定",
                "latencyMs", 23
        )));

        mockMvc().perform(get("/api/v1/novels/10001/rag/retrieval-logs")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].query").value("主角设定"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 删除文档成功。
    void UT_RAG_DOCUMENT_DELETE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RAG-DOC-DELETE";

        mockMvc().perform(delete("/api/v1/novels/10001/rag/documents/9001")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 解析文档成功。
    void UT_RAG_PARSE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RAG-PARSE";
        RagDocument doc = new RagDocument();
        doc.setId(9001L);
        when(ragApplicationService.parseDocument(eq(10001L), eq(9001L), any(), eq(traceId))).thenReturn(doc);

        mockMvc().perform(post("/api/v1/novels/10001/rag/documents/9001/parse")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9001));
    }

    @Test
    // 向量化成功。
    void UT_RAG_EMBED_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RAG-EMBED";
        RagDocument doc = new RagDocument();
        doc.setId(9001L);
        when(ragApplicationService.embedDocument(eq(10001L), eq(9001L), any(), eq(traceId))).thenReturn(doc);

        mockMvc().perform(post("/api/v1/novels/10001/rag/documents/9001/embed")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9001));
    }

    @Test
    // 索引状态查询成功。
    void UT_RAG_INDEX_STATUS_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-RAG-INDEX-STATUS";
        when(ragApplicationService.getIndexStatus(10001L, 9001L)).thenReturn(Map.of("status", "indexed"));

        mockMvc().perform(get("/api/v1/novels/10001/rag/documents/9001/index-status")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("indexed"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }
}

