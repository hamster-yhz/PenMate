package com.penmate.backend.application.agent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class StaticToolMetadataRegistryTest {

    @Test
    void UT_APP_AGENT_TOOL_METADATA_REGISTRY_RESOLVES_CONTEXT_ENHANCER_AND_BOOK_CRUD() {
        Object registry = instantiate("com.penmate.backend.application.agent.StaticToolMetadataRegistry");

        Object contextEnhancerMetadata = invoke(registry, "getRequired", "context_enhancer");
        assertThat(invokeAccessor(contextEnhancerMetadata, "toolCode")).isEqualTo("context_enhancer");
        assertThat(invokeAccessor(contextEnhancerMetadata, "displayName")).isEqualTo("上下文增强");

        Object bookCrudMetadata = invoke(registry, "getRequired", "book_crud");
        assertThat(invokeAccessor(bookCrudMetadata, "toolCode")).isEqualTo("book_crud");
        assertThat(invokeAccessor(bookCrudMetadata, "displayName")).isEqualTo("书籍 CRUD");
    }

    private Object instantiate(String className) {
        try {
            Class<?> type = Class.forName(className);
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception ex) {
            throw new AssertionError("expected class to be constructible: " + className, ex);
        }
    }

    private Object invoke(Object target, String methodName, Object arg) {
        try {
            Method method = target.getClass().getMethod(methodName, arg.getClass());
            return method.invoke(target, arg);
        } catch (Exception ex) {
            throw new AssertionError("expected method invocation to succeed: " + methodName, ex);
        }
    }

    private Object invokeAccessor(Object target, String accessorName) {
        try {
            Method method = target.getClass().getMethod(accessorName);
            return method.invoke(target);
        } catch (Exception ex) {
            throw new AssertionError("expected accessor invocation to succeed: " + accessorName, ex);
        }
    }
}
