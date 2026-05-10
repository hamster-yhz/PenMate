package com.penmate.backend.interfaces.api.model;

import com.penmate.backend.application.agent.orchestration.AgentGenerationWorkflowDispatcher;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionRecoveryAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.approval.ApprovalApplicationService;
import com.penmate.backend.application.auth.AuthApplicationService;
import com.penmate.backend.application.iam.IamQueryApplicationService;
import com.penmate.backend.application.model.ModelApplicationService;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.application.ops.OpsApplicationService;
import com.penmate.backend.application.plugin.PluginApplicationService;
import com.penmate.backend.application.rag.RagApplicationService;
import com.penmate.backend.application.style.StyleApplicationService;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.GenerationStreamService;
import com.penmate.backend.interfaces.api.agent.AgentController;
import com.penmate.backend.interfaces.api.approval.ApprovalController;
import com.penmate.backend.interfaces.api.auth.AuthController;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import com.penmate.backend.interfaces.api.novel.NovelController;
import com.penmate.backend.interfaces.api.ops.OpsController;
import com.penmate.backend.interfaces.api.plugin.PluginController;
import com.penmate.backend.interfaces.api.rag.RagController;
import com.penmate.backend.interfaces.api.rbac.RbacQueryController;
import com.penmate.backend.interfaces.api.style.StyleController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = LegacyRouteExposureMvcTest.MvcOnlyTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc(addFilters = false)
class LegacyRouteExposureMvcTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
    })
    @Import({
            GlobalExceptionHandler.class,
            ApprovalController.class,
            AgentController.class,
            AuthController.class,
            ModelController.class,
            NovelController.class,
            OpsController.class,
            PluginController.class,
            RagController.class,
            RbacQueryController.class,
            StyleController.class
    })
    static class MvcOnlyTestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApprovalApplicationService approvalApplicationService;

    @MockBean
    private AgentConversationAppService agentConversationAppService;

    @MockBean
    private AgentSessionRecoveryAppService agentSessionRecoveryAppService;

    @MockBean
    private AgentTurnAppService agentTurnAppService;

    @MockBean
    private GenerationStreamService generationStreamService;

    @MockBean
    private AgentGenerationWorkflowDispatcher agentGenerationWorkflowDispatcher;

    @MockBean
    private AgentSessionRepository agentSessionRepository;

    @MockBean
    private AuthApplicationService authApplicationService;

    @MockBean
    private IamQueryApplicationService iamQueryApplicationService;

    @MockBean
    private ModelApplicationService modelApplicationService;

    @MockBean
    private NovelApplicationService novelApplicationService;

    @MockBean
    private OpsApplicationService opsApplicationService;

    @MockBean
    private PluginApplicationService pluginApplicationService;

    @MockBean
    private RagApplicationService ragApplicationService;

    @MockBean
    private StyleApplicationService styleApplicationService;

    @MockBean
    private SessionStyleBindingAppService sessionStyleBindingAppService;

    @Test
    void should_boot_agent_controller_mvc_context_after_dispatcher_dependency_added() throws Exception {
        mockMvc.perform(get("/api/v1/novels/920001/agent/sessions")
                        .header("X-Trace-Id", "trace-agent-mvc-boot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.traceId").value("trace-agent-mvc-boot"));
    }

    @Test
    void should_not_expose_legacy_model_policy_route_anywhere() throws Exception {
        String legacyRoute = "/api/v1/novels/920001/model-policies";

        mockMvc.perform(get(legacyRoute))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(legacyRoute))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(legacyRoute))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(legacyRoute))
                .andExpect(status().isNotFound());
    }
}
