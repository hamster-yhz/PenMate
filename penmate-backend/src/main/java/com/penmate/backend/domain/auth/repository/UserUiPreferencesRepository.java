package com.penmate.backend.domain.auth.repository;

import com.penmate.backend.domain.auth.model.UserUiPreferences;

public interface UserUiPreferencesRepository {
    UserUiPreferences findByUserId(Long userId);

    int upsert(UserUiPreferences preferences);
}
