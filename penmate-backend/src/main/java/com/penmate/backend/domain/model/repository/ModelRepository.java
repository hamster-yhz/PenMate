package com.penmate.backend.domain.model.repository;

import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderCapability;
import com.penmate.backend.domain.model.model.ModelUserPreferences;

import java.util.List;

public interface ModelRepository {

    List<ModelProvider> listProviders();

    ModelProvider findProvider(Long providerId);

    List<ModelProviderCapability> listCapabilities(Long providerId);

    ModelProviderCapability findCapability(Long providerId, String capabilityCode);

    List<ModelConfiguration> listAccessibleConfigurations(Long userId);

    ModelConfiguration findAccessibleConfiguration(Long userId, Long modelConfigId);

    ModelConfiguration findOwnedConfigurationForUpdate(Long actorUserId, Long modelConfigId, boolean systemScope);

    ModelCredential findCredential(ModelConfiguration configuration);

    int insertConfiguration(ModelConfiguration configuration);

    int updateConfiguration(ModelConfiguration configuration);

    int insertCredential(ModelConfiguration configuration, ModelCredential credential);

    int updateCredential(ModelConfiguration configuration, ModelCredential credential);

    int softDeleteConfiguration(ModelConfiguration configuration, Long actorUserId);

    int softDeleteCredential(ModelConfiguration configuration);

    boolean hasNonterminalRunReference(Long modelConfigId);

    List<Long> listDependentProjectIds(Long modelConfigId);

    List<Long> lockDependentProjectIds(Long modelConfigId);

    int markDependentProjectsReindexRequired(Long modelConfigId, String reason);

    int unbindDependentProjects(Long modelConfigId);

    int clearUserDefaultReferences(Long modelConfigId);

    boolean hasAnyReference(Long modelConfigId);

    ModelUserPreferences findUserPreferences(Long userId);

    int upsertUserPreferences(ModelUserPreferences preferences);

    boolean existsAccessibleActiveConfiguration(Long userId, Long modelConfigId, String modelType);
}
