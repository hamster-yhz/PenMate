package com.penmate.backend.infrastructure.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 项目级 WebSocket 通道配置。
 * <p>负责注册项目实时事件推送端点，使前端可按项目维度订阅生成状态与协作事件。</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ProjectWebSocketHandler projectWebSocketHandler;

    public WebSocketConfig(ProjectWebSocketHandler projectWebSocketHandler) {
        this.projectWebSocketHandler = projectWebSocketHandler;
    }

    /**
     * 注册 WebSocket 处理器。
     * <p><b>流程：</b>将项目通道处理器绑定到 `/ws/projects/*`，并放开跨域来源以适配多前端环境接入。</p>
     *
     * @param registry Spring WebSocket 处理器注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(projectWebSocketHandler, "/ws/projects/*")
                .setAllowedOriginPatterns("*");
    }
}

