package com.penmate.backend.interfaces.api.style;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.style.StyleApplicationService;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.style.model.StyleProfile;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StyleControllerTest {

    @Mock
    private StyleApplicationService styleApplicationService;

    @Mock
    private SessionStyleBindingAppService sessionStyleBindingAppService;

    @InjectMocks
    private StyleController styleController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(styleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void UT_STYLE_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-STYLE-LIST";
        StyleProfile style = new StyleProfile();
        style.setStyleId(301L);
        style.setProjectId(10001L);
        style.setName("叙事文风A");

        when(styleApplicationService.listStyles(10001L)).thenReturn(List.of(style));

        mockMvc().perform(get("/api/v1/novels/10001/styles")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].styleId").value("301"))
                .andExpect(jsonPath("$.data[0].name").value("叙事文风A"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_STYLE_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-STYLE-CREATE";
        StyleProfile created = new StyleProfile();
        created.setStyleId(302L);
        created.setProjectId(10001L);
        created.setName("终章加速风格");

        when(styleApplicationService.createStyle(eq(10001L), any(), eq(traceId))).thenReturn(created);

        mockMvc().perform(post("/api/v1/novels/10001/styles")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "终章加速风格",
                                "isDefault", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.styleId").value("302"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_STYLE_SWITCH_WARN_NOT_CONFIRMED_422() throws Exception {
        String traceId = "UT-TRACE-STYLE-SWITCH-WARN";
        doThrow(new IllegalArgumentException("STYLE_SWITCH_NOT_CONFIRMED"))
                .when(styleApplicationService).switchStyle(eq(10001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/styles/switch")
                        .param("operatorId", "1001")
                        .param("sessionId", "90001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "toStyleId", "301",
                                "warningConfirmed", false,
                                "reason", "测试"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_STYLE_ANALYZE_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-STYLE-ANALYZE-INVALID";

        mockMvc().perform(post("/api/v1/novels/10001/styles/analyze-sample")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of("sampleText", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_STYLE_DELETE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-STYLE-DELETE";
        doNothing().when(styleApplicationService).deleteStyle(anyLong(), anyLong(), anyLong(), eq(traceId));

        mockMvc().perform(delete("/api/v1/novels/10001/styles/301")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_STYLE_UPDATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-STYLE-UPDATE";
        StyleProfile updated = new StyleProfile();
        updated.setStyleId(301L);
        updated.setProjectId(10001L);
        updated.setName("更新文风");
        when(styleApplicationService.updateStyle(eq(10001L), eq(301L), any(), eq(traceId))).thenReturn(updated);

        mockMvc().perform(put("/api/v1/novels/10001/styles/301")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "更新文风",
                                "pace", "fast"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.styleId").value("301"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void UT_STYLE_SWITCH_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-STYLE-SWITCH-SUCCESS";
        StyleProfile style = new StyleProfile();
        style.setStyleId(302L);
        style.setProjectId(10001L);
        when(styleApplicationService.switchStyle(eq(10001L), any(), eq(traceId))).thenReturn(style);

        mockMvc().perform(post("/api/v1/novels/10001/styles/switch")
                        .param("operatorId", "1001")
                        .param("sessionId", "90001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "toStyleId", "302",
                                "warningConfirmed", true,
                                "reason", "测试切换"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.styleId").value("302"));

        verify(sessionStyleBindingAppService).bind(10001L, 90001L, 302L, 1001L, traceId);
    }

    @Test
    void UT_STYLE_SWITCH_NOT_FOUND() throws Exception {
        String traceId = "UT-TRACE-STYLE-SWITCH-NOT-FOUND";
        doThrow(new IllegalArgumentException("Style not found"))
                .when(styleApplicationService).switchStyle(eq(10001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/styles/switch")
                        .param("operatorId", "1001")
                        .param("sessionId", "90001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "toStyleId", "999",
                                "warningConfirmed", true,
                                "reason", "测试"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void UT_STYLE_ANALYZE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-STYLE-ANALYZE-SUCCESS";
        when(styleApplicationService.analyzeSample(eq(10001L), any(), eq(traceId)))
                .thenReturn(Map.of("pace", "fast", "tone", "serious"));

        mockMvc().perform(post("/api/v1/novels/10001/styles/analyze-sample")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of("sampleText", "这是一段样文内容"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pace").value("fast"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }
    @Test
    void UT_STYLE_REJECTS_LEGACY_PREFIX_IDS() throws Exception {
        String traceId = "UT-TRACE-STYLE-LEGACY-ID-REJECT";

        mockMvc().perform(get("/api/v1/novels/project-10001/styles")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }
}

