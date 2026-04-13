package com.penmate.backend.infrastructure.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ProjectWebSocketHandler projectWebSocketHandler;

    public WebSocketConfig(ProjectWebSocketHandler projectWebSocketHandler) {
        this.projectWebSocketHandler = projectWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(projectWebSocketHandler, "/ws/projects/*")
                .setAllowedOriginPatterns("*");
    }
}

