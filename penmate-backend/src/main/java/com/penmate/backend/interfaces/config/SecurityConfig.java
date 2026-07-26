package com.penmate.backend.interfaces.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.penmate.backend.application.iam.IamPermissionCodes.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
/**
 * Spring Security 基础配置。
 * <p>当前以无状态模式运行，开放鉴权与文档端点，并接入 TraceId 过滤器用于全链路日志追踪。</p>
 */
public class SecurityConfig {

    private final TraceIdFilter traceIdFilter;
    private final BearerAuthenticationFilter bearerAuthenticationFilter;

    public SecurityConfig(TraceIdFilter traceIdFilter, BearerAuthenticationFilter bearerAuthenticationFilter) {
        this.traceIdFilter = Objects.requireNonNull(traceIdFilter, "traceIdFilter");
        this.bearerAuthenticationFilter = Objects.requireNonNull(bearerAuthenticationFilter, "bearerAuthenticationFilter");
    }

    /**
     * 构建 HTTP 安全过滤链。
     * <p>禁用表单登录/Basic/CSRF，放行鉴权与 API 文档相关端点，并将 TraceId 过滤器前置。</p>
     *
     * @param http Spring Security HTTP 配置对象
     * @return 可供 Spring Security 注册的过滤链
     * @throws Exception 安全链构建过程中抛出的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        Objects.requireNonNull(http, "http must not be null");
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh"
                        ).permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/profile/menus").access(require(APP_ACCESS))
                        .requestMatchers(HttpMethod.GET, "/api/v1/author-profile").access(require(PROFILE_READ))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/author-profile").access(require(PROFILE_WRITE))

                        .requestMatchers("/api/v1/novels/imports/**").access(require(NOVEL_IMPORT))
                        .requestMatchers(HttpMethod.GET, "/api/v1/novels/*/exports/**").access(require(NOVEL_EXPORT))
                        .requestMatchers("/api/v1/novels/*/agent/**").access(require(AGENT_USE))
                        .requestMatchers("/api/v1/novels/*/approvals/**").access(require(AGENT_USE))
                        .requestMatchers(HttpMethod.POST, "/api/v1/novels/*/styles/analyze-sample")
                        .access(require(NOVEL_WRITE, AGENT_USE))
                        .requestMatchers(HttpMethod.GET, "/api/v1/novels/*/styles/**").access(require(NOVEL_READ))
                        .requestMatchers("/api/v1/novels/*/styles/**").access(require(NOVEL_WRITE))
                        .requestMatchers(HttpMethod.GET, "/api/v1/novels/*/story-bible/**").access(require(STORY_BIBLE_READ))
                        .requestMatchers("/api/v1/novels/*/story-bible/**").access(require(STORY_BIBLE_WRITE))
                        .requestMatchers(HttpMethod.GET, "/api/v1/novels/*/rag/**").access(require(RAG_READ))
                        .requestMatchers("/api/v1/novels/*/rag/**").access(require(RAG_WRITE))
                        .requestMatchers(HttpMethod.GET, "/api/v1/plugins/**", "/api/v1/novels/*/plugins/**")
                        .access(require(PLUGIN_READ))
                        .requestMatchers("/api/v1/plugins/**", "/api/v1/novels/*/plugins/**").access(require(PLUGIN_WRITE))

                        .requestMatchers("/api/v1/model/system-*", "/api/v1/model/system-*/**")
                        .access(require(MODEL_SYSTEM_WRITE))
                        .requestMatchers(HttpMethod.GET, "/api/v1/model/**").access(require(MODEL_USER_USE))
                        .requestMatchers("/api/v1/model/**").access(require(MODEL_USER_WRITE))

                        .requestMatchers(HttpMethod.GET, "/api/v1/users", "/api/v1/users/*", "/api/v1/users/*/roles")
                        .access(require(RBAC_USER_READ))
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").access(require(RBAC_USER_WRITE))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/*").access(require(RBAC_USER_WRITE))
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/*/restore-deletion").access(require(RBAC_USER_WRITE))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*").access(require(RBAC_USER_DELETE))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/roles").access(require(RBAC_USER_BIND_ROLE))
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles").access(require(RBAC_ROLE_READ))
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles/*/permissions").access(require(RBAC_PERMISSION_READ))
                        .requestMatchers(HttpMethod.POST, "/api/v1/roles").access(require(RBAC_ROLE_WRITE))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/roles/*").access(require(RBAC_ROLE_WRITE))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/roles/*").access(require(RBAC_ROLE_DELETE))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/roles/*/permissions")
                        .access(require(RBAC_ROLE_BIND_PERMISSION))
                        .requestMatchers(HttpMethod.GET, "/api/v1/permissions").access(require(RBAC_PERMISSION_READ))
                        .requestMatchers(HttpMethod.GET, "/api/v1/menus").access(require(RBAC_MENU_READ))

                        .requestMatchers(HttpMethod.GET, "/api/v1/jobs", "/api/v1/jobs/**").access(require(OPS_JOB_READ))
                        .requestMatchers(HttpMethod.POST, "/api/v1/jobs/**").access(require(OPS_JOB_WRITE))
                        .requestMatchers(HttpMethod.GET, "/api/v1/migrations/**").access(require(OPS_MIGRATION_READ))
                        .requestMatchers(HttpMethod.POST, "/api/v1/migrations/**").access(require(OPS_MIGRATION_WRITE))

                        .requestMatchers(HttpMethod.DELETE, "/api/v1/novels/trash/*").access(require(NOVEL_DELETE))
                        .requestMatchers(HttpMethod.GET, "/api/v1/novels/**").access(require(NOVEL_READ))
                        .requestMatchers("/api/v1/novels/**").access(require(NOVEL_WRITE))
                        .anyRequest().hasAuthority(APP_ACCESS))
                .addFilterBefore(bearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(traceIdFilter, BearerAuthenticationFilter.class);

        return http.build();
    }

    private static WebExpressionAuthorizationManager require(String... permissionCodes) {
        String expression = Stream.concat(Stream.of(APP_ACCESS), Stream.of(permissionCodes))
                .distinct()
                .map(code -> "hasAuthority('" + code + "')")
                .reduce((left, right) -> left + " and " + right)
                .orElse("authenticated");
        return new WebExpressionAuthorizationManager(expression);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置全局 CORS 规则。
     * <p>允许跨域访问并暴露 Authorization 响应头，满足前后端分离调试与联调需求。</p>
     *
     * @return CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", TraceIdFilter.TRACE_ID_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

