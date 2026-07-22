package com.penmate.backend.domain.iam.model;

import java.util.List;

/** A complete audit record for one atomic RBAC assignment replacement. */
public record IamRbacAssignmentAudit(
        Long auditId,
        Long actorUserId,
        String assignmentType,
        Long targetId,
        List<Long> beforeIds,
        List<Long> afterIds,
        Long previousRevision,
        Long newRevision,
        String traceId
) {
}
