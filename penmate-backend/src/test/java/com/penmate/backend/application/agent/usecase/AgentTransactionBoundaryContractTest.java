package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTransactionBoundaryContractTest {

    @Test
    void should_define_transaction_boundary_for_session_style_binding_write_use_case() throws Exception {
        Method method = SessionStyleBindingAppService.class.getMethod(
                "bind",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                String.class
        );

        assertThat(method.isAnnotationPresent(Transactional.class)
                || SessionStyleBindingAppService.class.isAnnotationPresent(Transactional.class))
                .as("session style binding write use case must be transactional")
                .isTrue();
    }

    @Test
    void should_define_transaction_boundary_for_agent_turn_creation_use_case() throws Exception {
        Method method = AgentTurnAppService.class.getMethod(
                "createTurn",
                Long.class,
                Long.class,
                AgentTurnCommand.class,
                String.class
        );

        assertThat(method.isAnnotationPresent(Transactional.class)
                || AgentTurnAppService.class.isAnnotationPresent(Transactional.class))
                .as("agent turn creation use case must be transactional")
                .isTrue();
    }
}
