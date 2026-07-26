package com.penmate.backend.application.iam;

import com.penmate.backend.application.common.exception.BusinessErrorType;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRbacAssignmentAudit;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Application use cases for revision-safe RBAC relationship replacement. */
@Service
public class IamRbacAssignmentApplicationService {
    private final IamGateway iamGateway;
    private final BusinessIdGenerator businessIdGenerator;
    private final AdminSafetyPolicy adminSafetyPolicy;
    private final AuthorizationChangeDispatcher authorizationChanges;

    public IamRbacAssignmentApplicationService(IamGateway iamGateway,
                                               BusinessIdGenerator businessIdGenerator,
                                               AdminSafetyPolicy adminSafetyPolicy,
                                               AuthorizationChangeDispatcher authorizationChanges) {
        this.iamGateway = iamGateway;
        this.businessIdGenerator = businessIdGenerator;
        this.adminSafetyPolicy = adminSafetyPolicy;
        this.authorizationChanges = authorizationChanges;
    }

    public RbacAssignmentSnapshot<IamRole> getUserRoleAssignments(Long userId) {
        IamUser user = iamGateway.findUserByUserId(userId);
        if (user == null) throw BusinessException.notFound("User not found");
        return new RbacAssignmentSnapshot<>(revision(user.getRbacRevision()), iamGateway.findRolesByUserId(userId));
    }

    public RbacAssignmentSnapshot<IamPermission> getRolePermissionAssignments(Long roleId) {
        IamRole role = requireRole(roleId);
        return new RbacAssignmentSnapshot<>(revision(role.getRbacRevision()), iamGateway.findPermissionsByRoleId(roleId));
    }

    @Transactional
    public RbacAssignmentSnapshot<IamRole> replaceUserRoles(Long userId,
                                                            List<Long> requestedRoleIds,
                                                            Long expectedRevision,
                                                            Long actorUserId,
                                                            String traceId) {
        List<Long> roleIds = uniqueIds(requestedRoleIds, "roleIds");
        Long actualRevision = iamGateway.lockUserRbacRevision(userId);
        if (actualRevision == null) throw BusinessException.notFound("User not found");
        requireExpectedRevision(expectedRevision, actualRevision);

        List<IamRole> selectedRoles = new ArrayList<>(roleIds.size());
        for (Long roleId : roleIds) selectedRoles.add(requireRole(roleId));
        List<IamRole> beforeRoles = iamGateway.findRolesByUserId(userId);
        List<Long> beforeIds = beforeRoles.stream()
                .map(IamRole::getRoleId).sorted().toList();
        List<Long> afterIds = roleIds.stream().sorted().toList();
        if (beforeIds.equals(afterIds)) return new RbacAssignmentSnapshot<>(actualRevision, selectedRoles);

        adminSafetyPolicy.requireRoleReplacementAllowed(actorUserId, userId, beforeRoles, selectedRoles);

        if (iamGateway.replaceUserRoles(userId, roleIds, actualRevision) != 1) {
            throw revisionConflict(expectedRevision, actualRevision);
        }
        Long nextRevision = actualRevision + 1;
        iamGateway.insertRbacAssignmentAudit(new IamRbacAssignmentAudit(
                businessIdGenerator.nextId(), actorUserId, "USER_ROLES", userId,
                beforeIds, afterIds, actualRevision, nextRevision, traceId));
        authorizationChanges.revokeSessionsAfterCommit(List.of(userId),
                "user:%d:roles:r%d".formatted(userId, nextRevision), actorUserId);
        return new RbacAssignmentSnapshot<>(nextRevision, selectedRoles);
    }

    @Transactional
    public RbacAssignmentSnapshot<IamPermission> replaceRolePermissions(Long roleId,
                                                                        List<Long> requestedPermissionIds,
                                                                        Long expectedRevision,
                                                                        Long actorUserId,
                                                                        String traceId) {
        List<Long> permissionIds = uniqueIds(requestedPermissionIds, "permissionIds");
        IamRole role = requireRole(roleId);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw BusinessException.forbidden("System role permissions are managed by the application baseline");
        }
        Long actualRevision = iamGateway.lockRoleRbacRevision(roleId);
        if (actualRevision == null) throw BusinessException.notFound("Role not found");
        requireExpectedRevision(expectedRevision, actualRevision);

        List<IamPermission> selectedPermissions = new ArrayList<>(permissionIds.size());
        for (Long permissionId : permissionIds) selectedPermissions.add(requirePermission(permissionId));
        List<Long> beforeIds = iamGateway.findPermissionsByRoleId(roleId).stream()
                .map(IamPermission::getPermissionId).sorted().toList();
        List<Long> afterIds = permissionIds.stream().sorted().toList();
        if (beforeIds.equals(afterIds)) return new RbacAssignmentSnapshot<>(actualRevision, selectedPermissions);

        if (iamGateway.replaceRolePermissions(roleId, permissionIds, actualRevision) != 1) {
            throw revisionConflict(expectedRevision, actualRevision);
        }
        Long nextRevision = actualRevision + 1;
        iamGateway.insertRbacAssignmentAudit(new IamRbacAssignmentAudit(
                businessIdGenerator.nextId(), actorUserId, "ROLE_PERMISSIONS", roleId,
                beforeIds, afterIds, actualRevision, nextRevision, traceId));
        List<Long> affectedUserIds = iamGateway.incrementAuthorizationVersionsByRoleId(roleId);
        authorizationChanges.revokeSessionsAfterCommit(affectedUserIds,
                "role:%d:permissions:r%d".formatted(roleId, nextRevision), actorUserId);
        return new RbacAssignmentSnapshot<>(nextRevision, selectedPermissions);
    }

    private IamRole requireRole(Long roleId) {
        IamRole role = iamGateway.findRoleByRoleId(roleId);
        if (role == null) throw BusinessException.notFound("Role not found: " + roleId);
        return role;
    }

    private IamPermission requirePermission(Long permissionId) {
        IamPermission permission = iamGateway.findPermissionByPermissionId(permissionId);
        if (permission == null) throw BusinessException.notFound("Permission not found: " + permissionId);
        return permission;
    }

    private List<Long> uniqueIds(List<Long> values, String field) {
        if (values == null) throw BusinessException.badRequest(field + " is required");
        if (values.stream().anyMatch(java.util.Objects::isNull)) {
            throw BusinessException.badRequest(field + " must not contain null");
        }
        Set<Long> unique = new LinkedHashSet<>(values);
        if (unique.size() != values.size()) {
            throw BusinessException.badRequest(field + " must not contain duplicates");
        }
        return List.copyOf(unique);
    }

    private void requireExpectedRevision(Long expectedRevision, Long actualRevision) {
        if (expectedRevision == null || expectedRevision < 0) {
            throw BusinessException.badRequest("expectedRevision must be zero or greater");
        }
        if (!expectedRevision.equals(actualRevision)) throw revisionConflict(expectedRevision, actualRevision);
    }

    private BusinessException revisionConflict(Long expected, Long actual) {
        return BusinessException.of(
                BusinessErrorType.CONFLICT,
                "RBAC_REVISION_CONFLICT",
                "RBAC assignments changed; refresh and try again",
                Map.of("expectedRevision", expected, "actualRevision", actual));
    }

    private Long revision(Long value) {
        return value == null ? 0L : value;
    }
}
