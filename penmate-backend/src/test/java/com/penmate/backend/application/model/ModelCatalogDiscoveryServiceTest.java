package com.penmate.backend.application.model;

import com.penmate.backend.application.model.command.ModelCommands.DiscoverModelsCommand;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.model.service.ModelCatalogGateway;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelCatalogDiscoveryServiceTest {
    @Mock private ModelRepository repository;
    @Mock private SecretCryptoService secretCryptoService;
    @Mock private ModelCatalogGateway catalogGateway;
    @InjectMocks private ModelCatalogDiscoveryService service;

    @Test
    void discoversNewModelsWithTheSubmittedKey() {
        when(repository.findProvider(11L)).thenReturn(provider("openai", "API_KEY"));
        when(repository.findCapability(11L, "CHAT")).thenReturn(new com.penmate.backend.domain.model.model.ModelProviderCapability());
        when(catalogGateway.discover(any())).thenReturn(List.of("gpt-5", "gpt-5-mini"));

        ModelCatalogDiscoveryService.DiscoveryResult result = service.discover(7L, false,
                new DiscoverModelsCommand(null, 11L, "CHAT", "https://example.test/v1", "secret"));

        assertThat(result.models()).containsExactly("gpt-5", "gpt-5-mini");
        ArgumentCaptor<ModelCatalogGateway.DiscoveryRequest> request = ArgumentCaptor.forClass(ModelCatalogGateway.DiscoveryRequest.class);
        verify(catalogGateway).discover(request.capture());
        assertThat(request.getValue().apiKey()).isEqualTo("secret");
    }

    @Test
    void reusesTheEncryptedKeyWhenEditing() {
        ModelConfiguration existing = new ModelConfiguration();
        existing.setModelConfigId(101L); existing.setScopeType("USER"); existing.setProviderId(11L);
        existing.setBaseUrl("https://example.test/v1");
        ModelCredential credential = new ModelCredential();
        credential.setStatus("ACTIVE"); credential.setEncryptedApiKey("cipher");
        when(repository.findAccessibleConfiguration(7L, 101L)).thenReturn(existing);
        when(repository.findProvider(11L)).thenReturn(provider("openai", "API_KEY"));
        when(repository.findCapability(11L, "CHAT")).thenReturn(new com.penmate.backend.domain.model.model.ModelProviderCapability());
        when(repository.findCredential(existing)).thenReturn(credential);
        when(secretCryptoService.decrypt("cipher")).thenReturn("stored-secret");
        when(catalogGateway.discover(any())).thenReturn(List.of("gpt-5"));

        service.discover(7L, false, new DiscoverModelsCommand(101L, null, "CHAT", null, null));

        ArgumentCaptor<ModelCatalogGateway.DiscoveryRequest> request = ArgumentCaptor.forClass(ModelCatalogGateway.DiscoveryRequest.class);
        verify(catalogGateway).discover(request.capture());
        assertThat(request.getValue().apiKey()).isEqualTo("stored-secret");
    }

    @Test
    void doesNotAllowAUserToDiscoverASystemConfiguration() {
        ModelConfiguration existing = new ModelConfiguration();
        existing.setScopeType("SYSTEM"); existing.setProviderId(11L);
        when(repository.findAccessibleConfiguration(7L, 101L)).thenReturn(existing);

        assertThatThrownBy(() -> service.discover(7L, false,
                new DiscoverModelsCommand(101L, null, "CHAT", null, "secret")))
                .hasMessage("Model configuration not found");
    }

    private ModelProvider provider(String code, String authType) {
        ModelProvider provider = new ModelProvider();
        provider.setProviderId(11L); provider.setCode(code); provider.setStatus("ACTIVE");
        provider.setAuthType(authType); provider.setBaseUrl("https://example.test/v1");
        return provider;
    }
}
