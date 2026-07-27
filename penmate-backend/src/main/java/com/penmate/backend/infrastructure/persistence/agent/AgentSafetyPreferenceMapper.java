package com.penmate.backend.infrastructure.persistence.agent;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentSafetyPreferenceMapper {
    @Select("SELECT safety_mode FROM user_agent_preferences WHERE user_id = #{userId}")
    String findByUserId(Long userId);

    @Insert("""
            INSERT INTO user_agent_preferences(user_id, safety_mode)
            VALUES(#{userId}, #{mode})
            ON CONFLICT(user_id) DO UPDATE SET safety_mode = EXCLUDED.safety_mode,
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsert(@Param("userId") Long userId, @Param("mode") String mode);
}
