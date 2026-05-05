package com.penmate.backend.application.agent;

import com.penmate.backend.domain.agent.service.AgentTaskTransitionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 领域服务装配配置。
 * <p>当前主要职责是把不依赖 Spring 的领域策略对象注册为容器 Bean，供应用层编排组件注入使用。</p>
 */
@Configuration
public class AgentDomainConfig {

    @Bean
    public AgentTaskTransitionPolicy agentTaskTransitionPolicy() {
        return new AgentTaskTransitionPolicy();
    }
}
