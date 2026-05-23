package com.penmate.backend.application.agent.query;

import com.penmate.backend.application.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSessionTokenUsageQueryServiceTest {

    @Test
    void should_return_session_token_usage_view_with_usage_ratio() throws Exception {
        Class<?> repositoryClass = Class.forName("com.penmate.backend.domain.agent.repository.AgentSessionRepository");
        Object repository = java.lang.reflect.Proxy.newProxyInstance(
                repositoryClass.getClassLoader(),
                new Class[]{repositoryClass},
                (proxy, method, args) -> {
                    if (method.getName().equals("findSessionTokenUsageSummary")) {
                        return java.util.Map.of(
                                "promptTokens", 48000,
                                "completionTokens", 16000,
                                "maxContextTokens", 128000,
                                "modelName", "gpt-4.1"
                        );
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                });

        Class<?> serviceClass = Class.forName("com.penmate.backend.application.agent.query.AgentSessionTokenUsageQueryService");
        Constructor<?> constructor = serviceClass.getDeclaredConstructor(repositoryClass);
        Object service = constructor.newInstance(repository);

        Method method = serviceClass.getMethod("getTokenUsage", Long.class, Long.class, String.class);
        Object view = method.invoke(service, 10001L, 90001L, "trace-token-usage-1");

        Method usedTokens = view.getClass().getMethod("usedTokens");
        Method maxContextTokens = view.getClass().getMethod("maxContextTokens");
        Method usageRatio = view.getClass().getMethod("usageRatio");
        Method promptTokens = view.getClass().getMethod("promptTokens");
        Method completionTokens = view.getClass().getMethod("completionTokens");
        Method modelName = view.getClass().getMethod("modelName");

        assertThat(usedTokens.invoke(view)).isEqualTo(64000);
        assertThat(maxContextTokens.invoke(view)).isEqualTo(128000);
        assertThat(usageRatio.invoke(view)).isEqualTo(0.5d);
        assertThat(promptTokens.invoke(view)).isEqualTo(48000);
        assertThat(completionTokens.invoke(view)).isEqualTo(16000);
        assertThat(modelName.invoke(view)).isEqualTo("gpt-4.1");
    }
    @Test
    void should_throw_not_found_when_session_token_usage_summary_is_missing() throws Exception {
        Class<?> repositoryClass = Class.forName("com.penmate.backend.domain.agent.repository.AgentSessionRepository");
        Object repository = java.lang.reflect.Proxy.newProxyInstance(
                repositoryClass.getClassLoader(),
                new Class[]{repositoryClass},
                (proxy, method, args) -> {
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
                });

        Class<?> serviceClass = Class.forName("com.penmate.backend.application.agent.query.AgentSessionTokenUsageQueryService");
        Constructor<?> constructor = serviceClass.getDeclaredConstructor(repositoryClass);
        Object service = constructor.newInstance(repository);
        Method method = serviceClass.getMethod("getTokenUsage", Long.class, Long.class, String.class);

        assertThatThrownBy(() -> method.invoke(service, 10001L, 90002L, "trace-token-usage-missing-1"))
                .hasCauseInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException cause = (BusinessException) ex.getCause();
                    assertThat(cause.getHttpStatus().value()).isEqualTo(404);
                    assertThat(cause.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
                });
    }
}
