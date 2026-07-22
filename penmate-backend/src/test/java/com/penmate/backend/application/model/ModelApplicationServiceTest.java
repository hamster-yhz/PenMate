package com.penmate.backend.application.model;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.model.command.ModelCommands.CreateConfigurationCommand;
import com.penmate.backend.application.model.command.ModelCommands.SaveUserModelPreferencesCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateConfigurationCommand;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderCapability;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelApplicationServiceTest {

    @Mock
    private ModelRepository repository;

    @Mock
    private BusinessIdGenerator idGenerator;

    @Mock
    private SecretCryptoService secretCryptoService;

    @InjectMocks
    private ModelApplicationService service;

    @Test
    void listsProvidersWithTheirCapabilities() {
        ModelProvider provider = provider(1L, "openai-compatible", "API_KEY");
        ModelProviderCapability chat = capability(1L, "CHAT", "OPENAI_CHAT_COMPLETIONS");
        when(repository.listProviders()).thenReturn(List.of(provider));
        when(repository.listCapabilities(1L)).thenReturn(List.of(chat));

        List<ModelApplicationService.ProviderView> result = service.listProviders();

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.provider()).isSameAs(provider);
            assertThat(view.capabilities()).containsExactly(chat);
        });
    }

    @Test
    void createsUserChatConfigurationAndEncryptedCredential() {
        ModelProvider provider = provider(1L, "openai-compatible", "API_KEY");
        when(repository.findProvider(1L)).thenReturn(provider);
        when(repository.findCapability(1L, "CHAT"))
                .thenReturn(capability(1L, "CHAT", "OPENAI_CHAT_COMPLETIONS"));
        when(idGenerator.nextId()).thenReturn(101L, 201L);
        when(repository.insertConfiguration(any())).thenReturn(1);
        when(secretCryptoService.encrypt("sk-secret-value")).thenReturn("ciphertext");
        when(repository.insertCredential(any(), any())).thenReturn(1);
        ModelConfiguration persisted = new ModelConfiguration();
        persisted.setModelConfigId(101L);
        when(repository.findAccessibleConfiguration(7L, 101L)).thenReturn(persisted);

        ModelConfiguration result = service.createConfiguration(7L, false,
                new CreateConfigurationCommand(1L, "Main", "chat", "gpt-4.1",
                        "https://models.example.test/v1/", null, "sk-secret-value", null, null));

        assertThat(result).isSameAs(persisted);
        ArgumentCaptor<ModelConfiguration> configuration = ArgumentCaptor.forClass(ModelConfiguration.class);
        verify(repository).insertConfiguration(configuration.capture());
        assertThat(configuration.getValue()).satisfies(value -> {
            assertThat(value.getScopeType()).isEqualTo("USER");
            assertThat(value.getOwnerUserId()).isEqualTo(7L);
            assertThat(value.getModelType()).isEqualTo("CHAT");
            assertThat(value.getBaseUrl()).isEqualTo("https://models.example.test/v1");
            assertThat(value.getContextWindowTurns()).isEqualTo(6);
            assertThat(value.getMaxContextTokens()).isEqualTo(128000);
            assertThat(value.getDistanceMetric()).isNull();
        });
        ArgumentCaptor<ModelCredential> credential = ArgumentCaptor.forClass(ModelCredential.class);
        verify(repository).insertCredential(eq(configuration.getValue()), credential.capture());
        assertThat(credential.getValue()).satisfies(value -> {
            assertThat(value.getCredentialId()).isEqualTo(201L);
            assertThat(value.getEncryptedApiKey()).isEqualTo("ciphertext");
            assertThat(value.getMaskedApiKey()).isEqualTo("****alue");
        });
    }

    @Test
    void createsEmbeddingConfigurationWithMetricAndNoCredentialForNoAuthProvider() {
        ModelProvider provider = provider(2L, "local", "NONE");
        when(repository.findProvider(2L)).thenReturn(provider);
        when(repository.findCapability(2L, "EMBEDDING"))
                .thenReturn(capability(2L, "EMBEDDING", "OPENAI_EMBEDDINGS"));
        when(idGenerator.nextId()).thenReturn(102L);
        when(repository.insertConfiguration(any())).thenReturn(1);

        service.createConfiguration(7L, false,
                new CreateConfigurationCommand(2L, "Local embedding", "EMBEDDING", "nomic-embed-text",
                        "http://localhost:11434/v1", "inner_product", null, null, null));

        ArgumentCaptor<ModelConfiguration> configuration = ArgumentCaptor.forClass(ModelConfiguration.class);
        verify(repository).insertConfiguration(configuration.capture());
        assertThat(configuration.getValue().getDistanceMetric()).isEqualTo("INNER_PRODUCT");
        verify(repository, never()).insertCredential(any(), any());
    }

    @Test
    void rejectsProviderWithoutRequestedCapability() {
        when(repository.findProvider(1L)).thenReturn(provider(1L, "chat-only", "API_KEY"));

        assertThatThrownBy(() -> service.createConfiguration(7L, false,
                new CreateConfigurationCommand(1L, "Embedding", "EMBEDDING", "embed-1",
                        null, null, "secret", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Provider does not support EMBEDDING");

        verify(repository, never()).insertConfiguration(any());
    }

    @Test
    void embeddingIdentityChangeLocksProjectsAndRequiresReindex() {
        ModelConfiguration existing = embeddingConfiguration(301L, "embed-old", "COSINE");
        ModelCredential credential = new ModelCredential();
        credential.setCredentialId(401L);
        when(repository.findOwnedConfigurationForUpdate(7L, 301L, false)).thenReturn(existing);
        when(repository.findProvider(1L)).thenReturn(provider(1L, "openai-compatible", "API_KEY"));
        when(repository.findCapability(1L, "EMBEDDING"))
                .thenReturn(capability(1L, "EMBEDDING", "OPENAI_EMBEDDINGS"));
        when(repository.findCredential(existing)).thenReturn(credential);
        when(repository.lockDependentProjectIds(301L)).thenReturn(List.of(11L, 12L));
        when(repository.updateConfiguration(any())).thenReturn(1);
        when(repository.updateCredential(any(), eq(credential))).thenReturn(1);

        service.updateConfiguration(7L, 301L, false,
                new UpdateConfigurationCommand(null, null, "embed-new", null,
                        null, null, null, null, null));

        verify(repository).lockDependentProjectIds(301L);
        verify(repository).markDependentProjectsReindexRequired(
                301L, "Embedding configuration changed; rebuild the project index");
        verify(secretCryptoService, never()).encrypt(any());
    }

    @Test
    void rejectsConfigurationMutationWhileReferencedByNonterminalRun() {
        ModelConfiguration existing = embeddingConfiguration(301L, "embed-old", "COSINE");
        when(repository.findOwnedConfigurationForUpdate(7L, 301L, false)).thenReturn(existing);
        when(repository.hasNonterminalRunReference(301L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateConfiguration(7L, 301L, false,
                new UpdateConfigurationCommand(null, "Renamed", null, null,
                        null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Model configuration is used by a nonterminal Agent Run");

        verify(repository, never()).updateConfiguration(any());
    }

    @Test
    void defaultsPreferencesToLlmSelectorWithoutEmbedding() {
        ModelUserPreferences result = service.getUserPreferences(7L);

        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getDefaultStoryBibleRoutingMode()).isEqualTo("LLM_SELECTOR");
        assertThat(result.getDefaultChunkTargetCharacters()).isEqualTo(800);
        assertThat(result.getDefaultChunkOverlapCharacters()).isEqualTo(120);
        assertThat(result.getDefaultChunkMaxCharacters()).isEqualTo(1200);
    }

    @Test
    void rejectsRetrievalDefaultWithoutEmbeddingModel() {
        SaveUserModelPreferencesCommand command = new SaveUserModelPreferencesCommand(
                null, null, null, "RETRIEVAL", 800, 120, 1200);

        assertThatThrownBy(() -> service.saveUserPreferences(7L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Projects without an Embedding model must default to LLM_SELECTOR");

        verify(repository, never()).upsertUserPreferences(any());
    }

    @Test
    void savesValidatedUserPreferences() {
        when(repository.existsAccessibleActiveConfiguration(7L, 501L, "CHAT")).thenReturn(true);
        when(repository.existsAccessibleActiveConfiguration(7L, 502L, "EMBEDDING")).thenReturn(true);
        when(repository.upsertUserPreferences(any())).thenReturn(1);
        ModelUserPreferences persisted = new ModelUserPreferences();
        persisted.setUserId(7L);
        when(repository.findUserPreferences(7L)).thenReturn(persisted);

        ModelUserPreferences result = service.saveUserPreferences(7L,
                new SaveUserModelPreferencesCommand(501L, null, 502L,
                        "retrieval_then_llm", 900, 150, 1300));

        assertThat(result).isSameAs(persisted);
        ArgumentCaptor<ModelUserPreferences> preferences = ArgumentCaptor.forClass(ModelUserPreferences.class);
        verify(repository).upsertUserPreferences(preferences.capture());
        assertThat(preferences.getValue()).satisfies(value -> {
            assertThat(value.getDefaultStoryBibleRoutingMode()).isEqualTo("RETRIEVAL_THEN_LLM");
            assertThat(value.getDefaultEmbeddingModelConfigId()).isEqualTo(502L);
            assertThat(value.getDefaultChunkTargetCharacters()).isEqualTo(900);
        });
    }

    private ModelProvider provider(Long id, String code, String authType) {
        ModelProvider provider = new ModelProvider();
        provider.setProviderId(id);
        provider.setCode(code);
        provider.setName(code);
        provider.setBaseUrl("https://api.example.test/v1");
        provider.setAuthType(authType);
        provider.setStatus("ACTIVE");
        return provider;
    }

    private ModelProviderCapability capability(Long providerId, String code, String protocol) {
        ModelProviderCapability capability = new ModelProviderCapability();
        capability.setProviderId(providerId);
        capability.setCapabilityCode(code);
        capability.setProtocolCode(protocol);
        capability.setStatus("ACTIVE");
        return capability;
    }

    private ModelConfiguration embeddingConfiguration(Long id, String modelName, String metric) {
        ModelConfiguration configuration = new ModelConfiguration();
        configuration.setModelConfigId(id);
        configuration.setScopeType("USER");
        configuration.setOwnerUserId(7L);
        configuration.setProviderId(1L);
        configuration.setDisplayName("Embedding");
        configuration.setModelType("EMBEDDING");
        configuration.setModelName(modelName);
        configuration.setBaseUrl("https://api.example.test/v1");
        configuration.setDistanceMetric(metric);
        configuration.setContextWindowTurns(6);
        configuration.setMaxContextTokens(128000);
        configuration.setStatus("ACTIVE");
        configuration.setCreatedBy(7L);
        return configuration;
    }
}
