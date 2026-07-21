package com.penmate.backend.interfaces.config;

import com.penmate.backend.domain.novel.repository.NovelGateway;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ProjectOwnershipInterceptorTest {

    @Test
    void skips_repeated_ownership_checks_for_async_sse_dispatches() {
        NovelGateway novels = mock(NovelGateway.class);
        ProjectOwnershipInterceptor interceptor = new ProjectOwnershipInterceptor(novels);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/novels/10/agent/runs/20/stream");
        request.setDispatcherType(DispatcherType.ASYNC);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verifyNoInteractions(novels);
    }
}
