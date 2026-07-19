package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderCapability;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import com.penmate.backend.domain.model.repository.ModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ModelRepositoryImpl implements ModelRepository {

    private final ModelMapper mapper;

    @Override public List<ModelProvider> listProviders() { return mapper.listProviders(); }
    @Override public ModelProvider findProvider(Long providerId) { return mapper.findProvider(providerId); }
    @Override public List<ModelProviderCapability> listCapabilities(Long providerId) { return mapper.listCapabilities(providerId); }
    @Override public ModelProviderCapability findCapability(Long providerId, String capabilityCode) {
        return mapper.findCapability(providerId, capabilityCode);
    }
    @Override public List<ModelConfiguration> listAccessibleConfigurations(Long userId) {
        return mapper.listAccessibleConfigurations(userId);
    }
    @Override public ModelConfiguration findAccessibleConfiguration(Long userId, Long modelConfigId) {
        return mapper.findAccessibleConfiguration(userId, modelConfigId);
    }
    @Override public ModelConfiguration findOwnedConfigurationForUpdate(Long actorUserId, Long modelConfigId, boolean systemScope) {
        return systemScope ? mapper.findSystemConfigurationForUpdate(modelConfigId)
                : mapper.findUserConfigurationForUpdate(actorUserId, modelConfigId);
    }
    @Override public ModelCredential findCredential(ModelConfiguration configuration) {
        return "SYSTEM".equals(configuration.getScopeType())
                ? mapper.findOfficialCredential(configuration.getModelConfigId())
                : mapper.findUserCredential(configuration.getOwnerUserId(), configuration.getModelConfigId());
    }
    @Override public int insertConfiguration(ModelConfiguration configuration) { return mapper.insertConfiguration(configuration); }
    @Override public int updateConfiguration(ModelConfiguration configuration) { return mapper.updateConfiguration(configuration); }
    @Override public int insertCredential(ModelConfiguration configuration, ModelCredential credential) {
        return "SYSTEM".equals(configuration.getScopeType())
                ? mapper.insertOfficialCredential(configuration, credential)
                : mapper.insertUserCredential(configuration, credential);
    }
    @Override public int updateCredential(ModelConfiguration configuration, ModelCredential credential) {
        return "SYSTEM".equals(configuration.getScopeType())
                ? mapper.updateOfficialCredential(configuration, credential)
                : mapper.updateUserCredential(configuration, credential);
    }
    @Override public int softDeleteConfiguration(ModelConfiguration configuration, Long actorUserId) {
        return mapper.softDeleteConfiguration(configuration, actorUserId);
    }
    @Override public int softDeleteCredential(ModelConfiguration configuration) {
        return "SYSTEM".equals(configuration.getScopeType())
                ? mapper.softDeleteOfficialCredential(configuration.getModelConfigId())
                : mapper.softDeleteUserCredential(configuration.getModelConfigId());
    }
    @Override public boolean hasNonterminalRunReference(Long modelConfigId) {
        return mapper.countNonterminalRunReferences(modelConfigId) > 0;
    }
    @Override public List<Long> listDependentProjectIds(Long modelConfigId) {
        return mapper.listDependentProjectIds(modelConfigId);
    }
    @Override public List<Long> lockDependentProjectIds(Long modelConfigId) {
        return mapper.lockDependentProjectIds(modelConfigId);
    }
    @Override public int markDependentProjectsReindexRequired(Long modelConfigId, String reason) {
        return mapper.markDependentProjectsReindexRequired(modelConfigId, reason);
    }
    @Override public int unbindDependentProjects(Long modelConfigId) { return mapper.unbindDependentProjects(modelConfigId); }
    @Override public int clearUserDefaultReferences(Long modelConfigId) { return mapper.clearUserDefaultReferences(modelConfigId); }
    @Override public boolean hasAnyReference(Long modelConfigId) { return mapper.countAllReferences(modelConfigId) > 0; }
    @Override public ModelUserPreferences findUserPreferences(Long userId) { return mapper.findUserPreferences(userId); }
    @Override public int upsertUserPreferences(ModelUserPreferences preferences) { return mapper.upsertUserPreferences(preferences); }
    @Override public boolean existsAccessibleActiveConfiguration(Long userId, Long modelConfigId, String modelType) {
        return mapper.countAccessibleActiveConfiguration(userId, modelConfigId, modelType) > 0;
    }
}
