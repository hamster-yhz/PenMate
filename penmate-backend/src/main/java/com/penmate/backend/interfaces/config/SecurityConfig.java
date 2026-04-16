package com.penmate.backend.interfaces.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
/**
 * Spring Security 基础配置。
 * <p>当前以无状态模式运行，开放鉴权与文档端点，并接入 TraceId 过滤器用于全链路日志追踪。</p>
 */
public class SecurityConfig {

    private final TraceIdFilter traceIdFilter;

    public SecurityConfig(TraceIdFilter traceIdFilter) {
        this.traceIdFilter = traceIdFilter;
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
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/auth/**"
                        ).permitAll()
                        .anyRequest().permitAll())
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

