package com.penmate.backend.infrastructure.persistence.auth;

import com.penmate.backend.domain.auth.model.UserUiPreferences;
import com.penmate.backend.domain.auth.repository.UserUiPreferencesRepository;
import org.springframework.stereotype.Repository;

@Repository
public class UserUiPreferencesRepositoryImpl implements UserUiPreferencesRepository {
    private final UserUiPreferencesMapper mapper;

    public UserUiPreferencesRepositoryImpl(UserUiPreferencesMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserUiPreferences findByUserId(Long userId) {
        return mapper.findByUserId(userId);
    }

    @Override
    public int upsert(UserUiPreferences preferences) {
        return mapper.upsert(preferences);
    }
}
