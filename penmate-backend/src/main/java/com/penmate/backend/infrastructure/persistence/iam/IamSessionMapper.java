package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.domain.iam.model.IamSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IamSessionMapper {

    @Insert("""
            INSERT INTO iam_user_sessions
            (user_id, access_token, refresh_token, access_expires_at, refresh_expires_at)
            VALUES
            (#{userId}, #{accessToken}, #{refreshToken}, #{accessExpiresAt}, #{refreshExpiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IamSession session);

    @Select("""
            SELECT id, user_id, access_token, refresh_token, access_expires_at, refresh_expires_at, revoked_at
            FROM iam_user_sessions
            WHERE access_token = #{accessToken} AND revoked_at IS NULL
            """)
    IamSession findByAccessToken(@Param("accessToken") String accessToken);

    @Select("""
            SELECT id, user_id, access_token, refresh_token, access_expires_at, refresh_expires_at, revoked_at
            FROM iam_user_sessions
            WHERE refresh_token = #{refreshToken} AND revoked_at IS NULL
            """)
    IamSession findByRefreshToken(@Param("refreshToken") String refreshToken);

    @Update("""
            UPDATE iam_user_sessions
            SET revoked_at = CURRENT_TIMESTAMP(3)
            WHERE access_token = #{accessToken} AND revoked_at IS NULL
            """)
    int revokeByAccessToken(@Param("accessToken") String accessToken);

    @Update("""
            UPDATE iam_user_sessions
            SET access_token = #{accessToken},
                refresh_token = #{refreshToken},
                access_expires_at = #{accessExpiresAt},
                refresh_expires_at = #{refreshExpiresAt}
            WHERE id = #{id} AND revoked_at IS NULL
            """)
    int rotate(IamSession session);
}

