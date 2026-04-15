package com.penmate.backend.infrastructure.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocketConfig。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ProjectWebSocketHandler projectWebSocketHandler;

    public WebSocketConfig(ProjectWebSocketHandler projectWebSocketHandler) {
        this.projectWebSocketHandler = projectWebSocketHandler;
    }

    /**
     * 处理业务请求。
     *
     * @param registry 入参：registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(projectWebSocketHandler, "/ws/projects/*")
                .setAllowedOriginPatterns("*");
    }
}

