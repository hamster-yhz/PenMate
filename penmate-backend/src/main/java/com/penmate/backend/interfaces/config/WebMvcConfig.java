package com.penmate.backend.interfaces.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final ProjectOwnershipInterceptor projectOwnershipInterceptor;

    public WebMvcConfig(ProjectOwnershipInterceptor projectOwnershipInterceptor) {
        this.projectOwnershipInterceptor = projectOwnershipInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(projectOwnershipInterceptor).addPathPatterns("/api/v1/novels/*/**");
    }
}
