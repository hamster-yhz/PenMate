package com.penmate.backend.infrastructure.persistence.iam;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IamRbacAuditMapper {

    @Insert("""
            INSERT INTO iam_rbac_assignment_audits(
                audit_id, actor_user_id, assignment_type, target_id,
                before_ids_json, after_ids_json, previous_revision, new_revision, trace_id
            ) VALUES (
                #{auditId}, #{actorUserId}, #{assignmentType}, #{targetId},
                #{beforeIdsJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler},
                #{afterIdsJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler},
                #{previousRevision}, #{newRevision}, #{traceId}
            )
            """)
    int insert(@Param("auditId") Long auditId,
               @Param("actorUserId") Long actorUserId,
               @Param("assignmentType") String assignmentType,
               @Param("targetId") Long targetId,
               @Param("beforeIdsJson") String beforeIdsJson,
               @Param("afterIdsJson") String afterIdsJson,
               @Param("previousRevision") Long previousRevision,
               @Param("newRevision") Long newRevision,
               @Param("traceId") String traceId);
}
