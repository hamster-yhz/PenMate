package com.penmate.backend.interfaces.api.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.plugin.PluginApplicationService;
import com.penmate.backend.domain.plugin.model.PluginCatalogItem;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PluginControllerTest {

    @Mock
    private PluginApplicationService pluginApplicationService;

    @InjectMocks
    private PluginController pluginController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(pluginController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    // 插件目录查询成功。
    void UT_PLUGIN_CATALOG_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-PLUGIN-CATALOG-LIST";
        PluginCatalogItem item = new PluginCatalogItem();
        item.setId(1L);
        item.setCode("knowledge-rag");
        item.setName("知识检索插件");
        when(pluginApplicationService.listCatalog()).thenReturn(List.of(item));

        mockMvc().perform(get("/api/v1/plugins/catalog").header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("knowledge-rag"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 插件目录详情不存在。
    void UT_PLUGIN_CATALOG_DETAIL_NOT_FOUND() throws Exception {
        String traceId = "UT-TRACE-PLUGIN-CATALOG-NOT-FOUND";
        doThrow(new IllegalArgumentException("Plugin catalog not found"))
                .when(pluginApplicationService).getCatalog("missing-plugin");

        mockMvc().perform(get("/api/v1/plugins/catalog/missing-plugin")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 安装插件成功。
    void UT_PLUGIN_INSTALL_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-PLUGIN-INSTALL";
        doNothing().when(pluginApplicationService).install(eq(10001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/plugins/install")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pluginCode", "knowledge-rag",
                                "version", "1.0.0",
                                "configJson", "{}"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("installed"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 项目插件列表响应中的业务 ID 应为 string 语义字段。
    void UT_PLUGIN_PROJECT_LIST_RESPONSE_BUSINESS_IDS_ARE_STRING_FIELDS() throws Exception {
        String traceId = "UT-TRACE-PLUGIN-PROJECT-LIST-STRING-ID";
        com.penmate.backend.domain.plugin.model.PluginProjectInstall install = new com.penmate.backend.domain.plugin.model.PluginProjectInstall();
        install.setProjectId(10001L);
        install.setPluginInstallId(8001L);
        install.setInstalledBy(1001L);
        install.setPluginCode("knowledge-rag");
        when(pluginApplicationService.listProjectInstalls(10001L)).thenReturn(List.of(install));

        mockMvc().perform(get("/api/v1/novels/10001/plugins")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].projectId").isString())
                .andExpect(jsonPath("$.data[0].pluginInstallId").isString())
                .andExpect(jsonPath("$.data[0].installedBy").isString())
                .andExpect(jsonPath("$.data[0].projectId").value("10001"))
                .andExpect(jsonPath("$.data[0].pluginInstallId").value("8001"))
                .andExpect(jsonPath("$.data[0].installedBy").value("1001"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 安装插件重复冲突。
    void UT_PLUGIN_INSTALL_DUPLICATE_409() throws Exception {
        String traceId = "UT-TRACE-PLUGIN-INSTALL-DUPLICATE";
        doThrow(new IllegalArgumentException("Plugin already installed"))
                .when(pluginApplicationService).install(eq(10001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/plugins/install")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pluginCode", "knowledge-rag"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 安装插件参数错误。
    void UT_PLUGIN_INSTALL_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-PLUGIN-INSTALL-INVALID";

        mockMvc().perform(post("/api/v1/novels/10001/plugins/install")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pluginCode", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 插件启停更新成功。
    void UT_PLUGIN_UPDATE_ENABLE_DISABLE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-PLUGIN-UPDATE";
        doNothing().when(pluginApplicationService).updateInstall(eq(10001L), eq("knowledge-rag"), any(), eq(traceId));

        mockMvc().perform(patch("/api/v1/novels/10001/plugins/knowledge-rag")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "enabled", false,
                                "configJson", "{}"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 插件卸载成功。
    void UT_PLUGIN_DELETE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-PLUGIN-DELETE";
        doNothing().when(pluginApplicationService).deleteInstall(10001L, "knowledge-rag", 1001L, traceId);

        mockMvc().perform(delete("/api/v1/novels/10001/plugins/knowledge-rag")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 插件调用日志分页查询成功。
    void UT_PLUGIN_CALL_LOGS_PAGINATION_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-PLUGIN-LOGS";
        com.penmate.backend.domain.plugin.model.PluginCallLog log = new com.penmate.backend.domain.plugin.model.PluginCallLog();
        log.setId(1L);
        log.setPluginCode("knowledge-rag");
        when(pluginApplicationService.listCallLogs(10001L)).thenReturn(List.of(log));

        mockMvc().perform(get("/api/v1/novels/10001/plugins/call-logs")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pluginCode").value("knowledge-rag"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }
}

