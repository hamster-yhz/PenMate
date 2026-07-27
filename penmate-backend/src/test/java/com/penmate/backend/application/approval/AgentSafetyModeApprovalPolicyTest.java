package com.penmate.backend.application.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.tool.definition.LedgerCrudToolDefinition;
import com.penmate.backend.application.agent.tool.definition.StoryBibleV2ToolDefinitions;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSafetyModeApprovalPolicyTest {
    private final DefaultApprovalPolicyEngine engine = new DefaultApprovalPolicyEngine(
            new JacksonJsonCodec(new ObjectMapper()));

    @Test
    void applies_the_run_snapshot_threshold_to_backend_defined_risk() {
        var ledger = new LedgerCrudToolDefinition().descriptor();
        assertThat(evaluate(ledger, "{\"operation\":\"update\"}", "STRICT").approvalRequired()).isTrue();
        assertThat(evaluate(ledger, "{\"operation\":\"delete\"}", "STANDARD").approvalRequired()).isFalse();

        var structure = StoryBibleV2ToolDefinitions.structureWrite().descriptor();
        assertThat(evaluate(structure, "{\"items\":[{\"operation\":\"create_tag\"}]}", "AUTONOMOUS")
                .approvalRequired()).isTrue();
        assertThat(evaluate(structure, "{\"items\":[{\"operation\":\"create_tag\"}]}", "FULL_AUTHORITY")
                .approvalRequired()).isFalse();
    }

    private ApprovalPolicyDecision evaluate(com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor descriptor,
                                            String arguments, String mode) {
        return engine.evaluate(descriptor,
                new ToolCallRequest(1L, descriptor.toolCode(), arguments, null, "call", 1L), mode);
    }
}
