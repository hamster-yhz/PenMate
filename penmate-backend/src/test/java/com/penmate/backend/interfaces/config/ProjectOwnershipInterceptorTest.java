package com.penmate.backend.interfaces.config;

import com.penmate.backend.application.novel.security.ProjectAccessAuthorizer;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ProjectOwnershipInterceptorTest {

    @Test
    void skips_repeated_ownership_checks_for_async_sse_dispatches() {
        ProjectAccessAuthorizer projectAccess = mock(ProjectAccessAuthorizer.class);
        ProjectOwnershipInterceptor interceptor = new ProjectOwnershipInterceptor(projectAccess);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/novels/10/agent/runs/20/stream");
        request.setDispatcherType(DispatcherType.ASYNC);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verifyNoInteractions(projectAccess);
    }

    @Test
    void delegates_project_ownership_to_the_shared_authorizer() {
        ProjectAccessAuthorizer projectAccess = mock(ProjectAccessAuthorizer.class);
        ProjectOwnershipInterceptor interceptor = new ProjectOwnershipInterceptor(projectAccess);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/novels/10/agent");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("projectId", "10"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("20", null, java.util.List.of()));
        try {
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
            verify(projectAccess).requireOwnedProject(10L, 20L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
