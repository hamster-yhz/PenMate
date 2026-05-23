package com.penmate.backend.interfaces.api.agent;

import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerTokenUsageTest {

    @Test
    void should_return_not_found_when_session_token_usage_session_is_missing() throws Exception {
        String traceId = "UT-TRACE-AGENT-TOKEN-USAGE-MISSING";

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controllerWithRealTokenUsageChain(null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/novels/10001/agent/sessions/90002/token-usage")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    void should_return_session_token_usage_view() throws Exception {
        String traceId = "UT-TRACE-AGENT-TOKEN-USAGE";

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controllerWithTokenUsageStub(traceId))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/novels/10001/agent/sessions/90001/token-usage")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedTokens").value(64000))
                .andExpect(jsonPath("$.data.maxContextTokens").value(128000))
                .andExpect(jsonPath("$.data.usageRatio").value(0.5))
                .andExpect(jsonPath("$.data.promptTokens").value(48000))
                .andExpect(jsonPath("$.data.completionTokens").value(16000))
                .andExpect(jsonPath("$.data.modelName").value("gpt-4.1"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    private Object controllerWithTokenUsageStub(String traceId) throws Exception {
        Constructor<?> constructor = AgentController.class.getConstructors()[0];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        Map<String, Object> mocksByTypeName = new HashMap<>();
        for (int i = 0; i < parameterTypes.length; i++) {
            Object mock = Mockito.mock(parameterTypes[i]);
            args[i] = mock;
            mocksByTypeName.put(parameterTypes[i].getName(), mock);
        }

        Class<?> tokenUsageAppServiceClass = findClassOrNull("com.penmate.backend.application.agent.usecase.AgentSessionTokenUsageAppService");
        Class<?> tokenUsageViewClass = findClassOrNull("com.penmate.backend.application.agent.runtime.SessionTokenUsageView");
        if (tokenUsageAppServiceClass != null && tokenUsageViewClass != null) {
            Object appServiceMock = mocksByTypeName.get(tokenUsageAppServiceClass.getName());
            if (appServiceMock != null) {
                Object view = tokenUsageViewClass.getDeclaredConstructor(
                                Integer.class,
                                Integer.class,
                                Double.class,
                                Integer.class,
                                Integer.class,
                                String.class)
                        .newInstance(64000, 128000, 0.5d, 48000, 16000, "gpt-4.1");
                Object stubberTarget = Mockito.doReturn(view).when(appServiceMock);
                Method method = tokenUsageAppServiceClass.getMethod("getTokenUsage", Long.class, Long.class, String.class);
                method.invoke(stubberTarget, 10001L, 90001L, traceId);
            }
        }

        return constructor.newInstance(args);
    }

    private Object controllerWithRealTokenUsageChain(java.lang.reflect.InvocationHandler repositoryHandler) throws Exception {
        Constructor<?> constructor = AgentController.class.getConstructors()[0];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];

        Class<?> repositoryClass = Class.forName("com.penmate.backend.domain.agent.repository.AgentSessionRepository");
        Object repository = java.lang.reflect.Proxy.newProxyInstance(
                repositoryClass.getClassLoader(),
                new Class[]{repositoryClass},
                repositoryHandler == null ? (proxy, method, methodArgs) -> {
                    if (method.getName().equals("findSessionTokenUsageSummary")) {
                        return null;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                } : repositoryHandler
        );

        Class<?> queryServiceClass = Class.forName("com.penmate.backend.application.agent.query.AgentSessionTokenUsageQueryService");
        Object queryService = queryServiceClass.getDeclaredConstructor(repositoryClass).newInstance(repository);
        Class<?> appServiceClass = Class.forName("com.penmate.backend.application.agent.usecase.AgentSessionTokenUsageAppService");
        Object appService = appServiceClass.getDeclaredConstructor(queryServiceClass).newInstance(queryService);

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType.getName().equals(appServiceClass.getName())) {
                args[i] = appService;
            } else if (parameterType.getName().equals(repositoryClass.getName())) {
                args[i] = repository;
            } else {
                args[i] = Mockito.mock(parameterType);
            }
        }
        return constructor.newInstance(args);
    }

    private Class<?> findClassOrNull(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }
}
