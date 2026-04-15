package com.penmate.backend.application.auth;

import com.penmate.backend.application.auth.command.LoginCommand;
import com.penmate.backend.application.auth.command.RefreshCommand;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamSession;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.shared.service.AuditService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AuthApplicationService。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
@Service
public class AuthApplicationService {

    private final IamGateway iamGateway;
    private final AuditService auditService;

    public AuthApplicationService(IamGateway iamGateway,
                                  AuditService auditService) {
        this.iamGateway = iamGateway;
        this.auditService = auditService;
    }

    /**
     * 执行登录流程。
     *
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public Map<String, Object> login(LoginCommand command, String traceId) {
        IamUser user = iamGateway.findUserByEmail(command.email());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        if (!command.password().equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        IamSession session = new IamSession();
        session.setUserId(user.getId());
        session.setAccessToken("atk_" + UUID.randomUUID());
        session.setRefreshToken("rtk_" + UUID.randomUUID());
        session.setAccessExpiresAt(LocalDateTime.now().plusHours(2));
        session.setRefreshExpiresAt(LocalDateTime.now().plusDays(7));
        if (iamGateway.insertSession(session) != 1) {
            throw new IllegalArgumentException("Failed to create session");
        }
        iamGateway.touchLastLogin(user.getId());

        writeAudit(traceId, user.getId(), "auth", "login", "iam_user_sessions", String.valueOf(session.getId()), command.email(), 200);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", session.getAccessToken());
        result.put("refreshToken", session.getRefreshToken());
        result.put("accessExpiresAt", session.getAccessExpiresAt());
        result.put("refreshExpiresAt", session.getRefreshExpiresAt());
        return result;
    }

    /**
     * 执行登出流程。
     *
     * @param accessToken 入参：accessToken
     * @param traceId 入参：traceId
     */
    public void logout(String accessToken, String traceId) {
        String token = extractBearer(accessToken);
        IamSession session = iamGateway.findSessionByAccessToken(token);
        if (session == null) {
            return;
        }
        iamGateway.revokeByAccessToken(token);
        writeAudit(traceId, session.getUserId(), "auth", "logout", "iam_user_sessions", String.valueOf(session.getId()), null, 200);
    }

    /**
     * 刷新鉴权凭证。
     *
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public Map<String, Object> refresh(RefreshCommand command, String traceId) {
        IamSession session = iamGateway.findSessionByRefreshToken(command.refreshToken());
        if (session == null || session.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token invalid or expired");
        }
        session.setAccessToken("atk_" + UUID.randomUUID());
        session.setRefreshToken("rtk_" + UUID.randomUUID());
        session.setAccessExpiresAt(LocalDateTime.now().plusHours(2));
        session.setRefreshExpiresAt(LocalDateTime.now().plusDays(7));
        if (iamGateway.rotateSession(session) != 1) {
            throw new IllegalArgumentException("Failed to refresh token");
        }
        writeAudit(traceId, session.getUserId(), "auth", "refresh", "iam_user_sessions", String.valueOf(session.getId()), null, 200);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", session.getAccessToken());
        result.put("refreshToken", session.getRefreshToken());
        result.put("accessExpiresAt", session.getAccessExpiresAt());
        result.put("refreshExpiresAt", session.getRefreshExpiresAt());
        return result;
    }

    /**
     * 处理业务请求。
     *
     * @param authorization 入参：authorization
     * @return 出参：处理结果
     */
    public Map<String, Object> me(String authorization) {
        String token = extractBearer(authorization);
        IamSession session = iamGateway.findSessionByAccessToken(token);
        if (session == null || session.getAccessExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Login required");
        }
        IamUser user = iamGateway.findUserById(session.getUserId());
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        List<IamRole> roles = iamGateway.findRolesByUserId(user.getId());
        List<IamPermission> permissions = iamGateway.findPermissionsByUserId(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("email", user.getEmail());
        result.put("displayName", user.getDisplayName());
        result.put("roles", roles);
        result.put("permissions", permissions);
        return result;
    }

    private String extractBearer(String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header missing Bearer token");
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        String finalTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        auditService.write(finalTraceId, userId, module, action, resourceType, resourceId, requestJson, responseCode);
    }
}

