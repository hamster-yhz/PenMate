package com.penmate.backend.infrastructure.persistence.auth;

import com.penmate.backend.domain.auth.model.AuthSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AuthSessionMapper {

    @Insert("""
            INSERT INTO auth_sessions(
                session_id, user_id, current_access_jti, current_refresh_jti_hash,
                device_name, browser_name, operating_system, user_agent, ip_address, refresh_expires_at
            ) VALUES (
                #{sessionId}, #{userId}, #{currentAccessJti}, #{currentRefreshJtiHash},
                #{deviceName}, #{browserName}, #{operatingSystem}, #{userAgent}, #{ipAddress}, #{refreshExpiresAt}
            )
            """)
    int insert(AuthSession session);

    @Select("""
            SELECT id, session_id, user_id, current_access_jti, current_refresh_jti_hash,
                   device_name, browser_name, operating_system, user_agent, ip_address,
                   created_at, last_seen_at, refresh_expires_at, revoked_at
            FROM auth_sessions
            WHERE session_id = #{sessionId} AND user_id = #{userId}
            """)
    AuthSession findByIdAndUser(@Param("sessionId") String sessionId, @Param("userId") Long userId);

    @Select("""
            SELECT id, session_id, user_id, current_access_jti, current_refresh_jti_hash,
                   device_name, browser_name, operating_system, user_agent, ip_address,
                   created_at, last_seen_at, refresh_expires_at, revoked_at
            FROM auth_sessions
            WHERE user_id = #{userId} AND revoked_at IS NULL AND refresh_expires_at > CURRENT_TIMESTAMP(3)
            ORDER BY last_seen_at DESC, id DESC
            """)
    List<AuthSession> listByUser(@Param("userId") Long userId);

    @Update("""
            UPDATE auth_sessions
            SET current_access_jti = #{accessJti}, current_refresh_jti_hash = #{refreshJtiHash},
                ip_address = #{ipAddress}, refresh_expires_at = #{refreshExpiresAt},
                last_seen_at = #{lastSeenAt}
            WHERE session_id = #{sessionId} AND user_id = #{userId}
              AND current_refresh_jti_hash = #{expectedRefreshJtiHash}
              AND revoked_at IS NULL AND refresh_expires_at > CURRENT_TIMESTAMP(3)
            """)
    int rotate(@Param("sessionId") String sessionId, @Param("userId") Long userId,
               @Param("expectedRefreshJtiHash") String expectedRefreshJtiHash,
               @Param("accessJti") String accessJti, @Param("refreshJtiHash") String refreshJtiHash,
               @Param("ipAddress") String ipAddress, @Param("refreshExpiresAt") Instant refreshExpiresAt,
               @Param("lastSeenAt") Instant lastSeenAt);

    @Update("""
            UPDATE auth_sessions
            SET revoked_at = #{revokedAt}
            WHERE session_id = #{sessionId} AND user_id = #{userId} AND revoked_at IS NULL
            """)
    int revoke(@Param("sessionId") String sessionId, @Param("userId") Long userId,
               @Param("revokedAt") Instant revokedAt);

    @Update("""
            UPDATE auth_sessions
            SET revoked_at = #{revokedAt}
            WHERE user_id = #{userId} AND revoked_at IS NULL
            """)
    int revokeAll(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    @Select("""
            UPDATE auth_sessions
            SET revoked_at = #{revokedAt}
            WHERE user_id = #{userId} AND session_id <> #{retainedSessionId} AND revoked_at IS NULL
            RETURNING id, session_id, user_id, current_access_jti, current_refresh_jti_hash,
                      device_name, browser_name, operating_system, user_agent, ip_address,
                      created_at, last_seen_at, refresh_expires_at, revoked_at
            """)
    List<AuthSession> revokeAllExcept(@Param("userId") Long userId,
                                      @Param("retainedSessionId") String retainedSessionId,
                                      @Param("revokedAt") Instant revokedAt);
}
