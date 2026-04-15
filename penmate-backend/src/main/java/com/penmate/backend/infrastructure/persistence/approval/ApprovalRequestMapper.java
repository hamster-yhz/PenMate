package com.penmate.backend.infrastructure.persistence.approval;

import com.penmate.backend.domain.approval.model.ApprovalRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * ApprovalRequestMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface ApprovalRequestMapper {

    @Insert("""
            INSERT INTO agent_approval_requests
            (project_id, task_id, approval_type, payload_json, risk_level, status, requested_by)
            VALUES
            (#{projectId}, #{taskId}, #{approvalType}, #{payloadJson}, #{riskLevel}, 'pending', #{requestedBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApprovalRequest approvalRequest);

    @Select("""
            SELECT id, project_id, task_id, approval_type, payload_json, risk_level, status,
                   requested_by, reviewed_by, reviewed_at, review_comment, created_at, updated_at
            FROM agent_approval_requests
            WHERE project_id = #{projectId}
            ORDER BY created_at DESC
            """)
    List<ApprovalRequest> findByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT id, project_id, task_id, approval_type, payload_json, risk_level, status,
                   requested_by, reviewed_by, reviewed_at, review_comment, created_at, updated_at
            FROM agent_approval_requests
            WHERE id = #{id}
            """)
    ApprovalRequest findById(@Param("id") Long id);

    @Update("""
            UPDATE agent_approval_requests
            SET status = 'approved', reviewed_by = #{reviewedBy}, reviewed_at = CURRENT_TIMESTAMP(3), review_comment = #{comment}
            WHERE id = #{id} AND status = 'pending'
            """)
    int approve(@Param("id") Long id, @Param("reviewedBy") Long reviewedBy, @Param("comment") String comment);

    @Update("""
            UPDATE agent_approval_requests
            SET status = 'rejected', reviewed_by = #{reviewedBy}, reviewed_at = CURRENT_TIMESTAMP(3), review_comment = #{comment}
            WHERE id = #{id} AND status = 'pending'
            """)
    int reject(@Param("id") Long id, @Param("reviewedBy") Long reviewedBy, @Param("comment") String comment);
}

