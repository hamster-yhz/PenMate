package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.RagUploadSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RagUploadSessionMapper {
    @Select("""
            SELECT upload_id, project_id, owner_user_id, doc_type, title, original_filename,
                   file_extension, declared_mime_type, expected_size, expected_checksum,
                   object_key, upload_token_hash, upload_status, expires_at, completed_at,
                   created_at, updated_at
            FROM rag_upload_sessions
            WHERE upload_id = #{uploadId}
            FOR UPDATE
            """)
    RagUploadSession findByIdForUpdate(@Param("uploadId") Long uploadId);

    @Insert("""
            INSERT INTO rag_upload_sessions(
                upload_id, project_id, owner_user_id, doc_type, title, original_filename,
                file_extension, declared_mime_type, expected_size, expected_checksum,
                object_key, upload_token_hash, upload_status, expires_at)
            VALUES(#{uploadId}, #{projectId}, #{ownerUserId}, #{docType}, #{title}, #{originalFilename},
                   #{fileExtension}, #{declaredMimeType}, #{expectedSize}, #{expectedChecksum},
                   #{objectKey}, #{uploadTokenHash}, #{uploadStatus}, #{expiresAt})
            """)
    int insert(RagUploadSession session);

    @Update("""
            UPDATE rag_upload_sessions
            SET upload_status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
            WHERE upload_id = #{uploadId} AND upload_status = 'PENDING'
            """)
    int markCompleted(@Param("uploadId") Long uploadId);

    @Update("""
            UPDATE rag_upload_sessions
            SET upload_status = 'REJECTED', updated_at = CURRENT_TIMESTAMP(3)
            WHERE upload_id = #{uploadId} AND upload_status = 'PENDING'
            """)
    int markRejected(@Param("uploadId") Long uploadId);
}
