package com.penmate.backend.infrastructure.persistence.author;

import com.penmate.backend.domain.author.model.AuthorProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthorProfileMapper {

    @Select("""
            SELECT id, user_id, default_language, collaboration_mode, default_pov, default_tense,
                   description_density, dialogue_preference, banned_expressions, long_term_memory,
                   created_at, updated_at
            FROM author_profiles
            WHERE user_id = #{userId}
            """)
    AuthorProfile findByUserId(Long userId);

    @Insert("""
            INSERT INTO author_profiles(
                user_id, default_language, collaboration_mode, default_pov, default_tense,
                description_density, dialogue_preference, banned_expressions, long_term_memory
            ) VALUES (
                #{userId}, #{defaultLanguage}, #{collaborationMode}, #{defaultPov}, #{defaultTense},
                #{descriptionDensity}, #{dialoguePreference}, #{bannedExpressions}, #{longTermMemory}
            )
            ON CONFLICT (user_id) DO UPDATE SET
                default_language = EXCLUDED.default_language,
                collaboration_mode = EXCLUDED.collaboration_mode,
                default_pov = EXCLUDED.default_pov,
                default_tense = EXCLUDED.default_tense,
                description_density = EXCLUDED.description_density,
                dialogue_preference = EXCLUDED.dialogue_preference,
                banned_expressions = EXCLUDED.banned_expressions,
                long_term_memory = EXCLUDED.long_term_memory,
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsert(AuthorProfile profile);
}
