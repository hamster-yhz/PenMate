package com.penmate.backend.application.iam;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.repository.IamGateway;
import org.springframework.stereotype.Component;

import java.util.List;

/** Transactional invariants that prevent administrator lockout. */
@Component
public class AdminSafetyPolicy {
    private final IamGateway iam;

    public AdminSafetyPolicy(IamGateway iam) {
        this.iam = iam;
    }

    public void requireRoleReplacementAllowed(Long actorUserId, Long targetUserId,
                                              List<IamRole> before, List<IamRole> after) {
        boolean hadAdmin = hasAdmin(before);
        boolean keepsAdmin = hasAdmin(after);
        if (!hadAdmin || keepsAdmin) return;
        lockAdminInvariant();
        if (SystemRoleCodes.BOOTSTRAP_ADMIN_USER_ID == targetUserId) {
            throw BusinessException.forbidden("The emergency administrator cannot be demoted");
        }
        if (targetUserId.equals(actorUserId)) {
            throw BusinessException.forbidden("Administrators cannot remove their own administrator role");
        }
        if (iam.countActiveUsersByRoleCode(SystemRoleCodes.ADMIN) <= 1) {
            throw BusinessException.conflict("At least one active administrator is required");
        }
    }

    public void requireAccountMutationAllowed(Long actorUserId, Long targetUserId) {
        if (SystemRoleCodes.BOOTSTRAP_ADMIN_USER_ID == targetUserId) {
            throw BusinessException.forbidden("The emergency administrator account is protected");
        }
        List<IamRole> roles = iam.findRolesByUserId(targetUserId);
        if (!hasAdmin(roles)) return;
        lockAdminInvariant();
        if (targetUserId.equals(actorUserId)) {
            throw BusinessException.forbidden("Administrators cannot disable or delete their own account");
        }
        if (iam.countActiveUsersByRoleCode(SystemRoleCodes.ADMIN) <= 1) {
            throw BusinessException.conflict("At least one active administrator is required");
        }
    }

    private void lockAdminInvariant() {
        IamRole admin = iam.findRoleByCode(SystemRoleCodes.ADMIN);
        if (admin == null || iam.lockRoleRbacRevision(admin.getRoleId()) == null) {
            throw BusinessException.of("Administrator role is unavailable");
        }
    }

    private boolean hasAdmin(List<IamRole> roles) {
        return roles != null && roles.stream().anyMatch(role -> SystemRoleCodes.ADMIN.equals(role.getCode()));
    }
}
