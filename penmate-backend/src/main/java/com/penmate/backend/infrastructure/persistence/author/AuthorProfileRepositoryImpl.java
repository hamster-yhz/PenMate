package com.penmate.backend.infrastructure.persistence.author;

import com.penmate.backend.domain.author.model.AuthorProfile;
import com.penmate.backend.domain.author.repository.AuthorProfileRepository;
import org.springframework.stereotype.Repository;

@Repository
public class AuthorProfileRepositoryImpl implements AuthorProfileRepository {
    private final AuthorProfileMapper mapper;

    public AuthorProfileRepositoryImpl(AuthorProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AuthorProfile findByUserId(Long userId) {
        return mapper.findByUserId(userId);
    }

    @Override
    public int upsert(AuthorProfile profile) {
        return mapper.upsert(profile);
    }
}
