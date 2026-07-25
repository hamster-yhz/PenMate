package com.penmate.backend.domain.author.repository;

import com.penmate.backend.domain.author.model.AuthorProfile;

public interface AuthorProfileRepository {
    AuthorProfile findByUserId(Long userId);
    int upsert(AuthorProfile profile);
}
