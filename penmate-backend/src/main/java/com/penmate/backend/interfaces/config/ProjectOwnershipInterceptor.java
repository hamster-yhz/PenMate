package com.penmate.backend.interfaces.config;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.interfaces.api.common.AuthenticatedActor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Objects;

@Component
public class ProjectOwnershipInterceptor implements HandlerInterceptor {
    private final NovelGateway novels;

    public ProjectOwnershipInterceptor(NovelGateway novels) {
        this.novels = novels;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) return true;
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables) || variables.get("projectId") == null) return true;
        Long projectId = parseId(String.valueOf(variables.get("projectId")));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long actor = AuthenticatedActor.id(authentication);
        NovelProject project = novels.findProjectById(projectId);
        if (project == null || !Objects.equals(project.getOwnerUserId(), actor)) {
            // Do not disclose whether another user's project exists.
            throw BusinessException.notFound("Novel project not found");
        }
        return true;
    }

    private Long parseId(String value) {
        if (!value.matches("\\d+")) throw BusinessException.badRequest("projectId must be numeric");
        try { return Long.valueOf(value); }
        catch (NumberFormatException exception) { throw BusinessException.badRequest("projectId is out of range"); }
    }
}
