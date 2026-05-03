package com.penmate.backend.application.agent;

import com.penmate.backend.domain.agent.service.AgentTaskTransitionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentDomainConfig {

    @Bean
    public AgentTaskTransitionPolicy agentTaskTransitionPolicy() {
        return new AgentTaskTransitionPolicy();
    }
}
