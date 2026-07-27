package com.penmate.backend.application.agent.tool.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.approval.DefaultApprovalPolicyEngine;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerCrudToolDefinitionTest {
    private final AgentToolDescriptor descriptor = new LedgerCrudToolDefinition().descriptor();
    private final DefaultApprovalPolicyEngine policy = new DefaultApprovalPolicyEngine(
            new JacksonJsonCodec(new ObjectMapper()));

    @Test
    void assigns_operation_risk_in_backend_definition() {
        assertThat(risk("list")).isZero();
        assertThat(risk("read")).isZero();
        assertThat(risk("create")).isEqualTo(1);
        assertThat(risk("update")).isEqualTo(1);
        assertThat(risk("delete")).isEqualTo(2);
    }

    private int risk(String operation) {
        return policy.evaluate(descriptor, new ToolCallRequest(
                1L, "ledger_crud", "{\"operation\":\"" + operation + "\"}", null, "call", 1L)).riskLevel();
    }
}
