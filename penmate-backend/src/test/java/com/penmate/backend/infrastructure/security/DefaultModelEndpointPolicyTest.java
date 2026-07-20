package com.penmate.backend.infrastructure.security;

import com.penmate.backend.application.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultModelEndpointPolicyTest {
    @Test
    void productionRejectsHttpCredentialsAndPrivateAddresses() {
        DefaultModelEndpointPolicy policy = new DefaultModelEndpointPolicy(false, "");

        assertThatThrownBy(() -> policy.validate("http://api.example.com/v1", false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> policy.validate("https://user:pass@example.com/v1", false))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.validate("https://127.0.0.1:11434/v1", false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("private or reserved");
        assertThatThrownBy(() -> policy.validate("https://169.254.169.254/latest/meta-data", false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("private or reserved");
    }

    @Test
    void localProfileAllowsLocalModelServers() {
        DefaultModelEndpointPolicy policy = new DefaultModelEndpointPolicy(true, "");

        assertThat(policy.validate("http://127.0.0.1:11434/v1/", false))
                .isEqualTo("http://127.0.0.1:11434/v1");
    }

    @Test
    void systemPrivateHostRequiresExplicitAllowlist() {
        DefaultModelEndpointPolicy denied = new DefaultModelEndpointPolicy(false, "");
        DefaultModelEndpointPolicy allowed = new DefaultModelEndpointPolicy(false, "127.0.0.1");

        assertThatThrownBy(() -> denied.validate("https://127.0.0.1/v1", true))
                .isInstanceOf(BusinessException.class);
        assertThat(allowed.validate("https://127.0.0.1/v1", true)).isEqualTo("https://127.0.0.1/v1");
    }
}
