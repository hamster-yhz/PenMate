package com.penmate.backend.application.auth;

import com.penmate.backend.application.auth.command.LoginCommand;
import com.penmate.backend.application.auth.command.RefreshCommand;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamSession;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.shared.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 认证应用服务。
 * <p>负责登录、登出、令牌刷新与当前登录用户信息查询，并在关键认证动作后记录审计日志。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthApplicationService {

    private final IamGateway iamGateway;
    private final AuditService auditService;

    /**
     * 处理邮箱密码登录。
     * <p>校验用户状态与密码后创建会话，签发 access/refresh token，并更新最后登录时间。</p>
     *
     * @param command 登录命令（邮箱、密码）
     * @param traceId 请求链路追踪 ID
     * @return 包含 accessToken、refreshToken 及过期时间的登录结果
     */
    public Map<String, Object> login(LoginCommand command, String traceId) {
        log.info("登录请求: email={}", command.email());
        IamUser user = iamGateway.findUserByEmail(command.email());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            log.warn("登录失败: email={}, reason=invalid_user_or_status", command.email());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Invalid credentials");
        }
        if (!command.password().equals(user.getPasswordHash())) {
            log.warn("登录失败: userId={}, reason=invalid_password", user.getId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Invalid credentials");
        }

        IamSession session = new IamSession();
        session.setUserId(user.getId());
        session.setAccessToken("atk_" + UUID.randomUUID());
        session.setRefreshToken("rtk_" + UUID.randomUUID());
        session.setAccessExpiresAt(LocalDateTime.now().plusHours(2));
        session.setRefreshExpiresAt(LocalDateTime.now().plusDays(7));
        if (iamGateway.insertSession(session) != 1) {
            log.error("登录失败: userId={}, reason=create_session_failed", user.getId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create session");
        }
        iamGateway.touchLastLogin(user.getId());

        writeAudit(traceId, user.getId(), "auth", "login", "iam_user_sessions", String.valueOf(session.getId()), command.email(), 200);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", session.getAccessToken());
        result.put("refreshToken", session.getRefreshToken());
        result.put("accessExpiresAt", session.getAccessExpiresAt());
        result.put("refreshExpiresAt", session.getRefreshExpiresAt());
        log.info("登录成功: userId={}, sessionId={}", user.getId(), session.getId());
        return result;
    }

    /**
     * 处理登出。
     * <p>根据 Bearer Token 定位会话并撤销访问令牌。</p>
     *
     * @param accessToken Authorization 头中的 Bearer Token
     * @param traceId 请求链路追踪 ID
     */
    public void logout(String accessToken, String traceId) {
        String token = extractBearer(accessToken);
        IamSession session = iamGateway.findSessionByAccessToken(token);
        if (session == null) {
            log.info("登出请求忽略: reason=session_not_found");
            return;
        }
        iamGateway.revokeByAccessToken(token);
        writeAudit(traceId, session.getUserId(), "auth", "logout", "iam_user_sessions", String.valueOf(session.getId()), null, 200);
        log.info("登出成功: userId={}, sessionId={}", session.getUserId(), session.getId());
    }

    /**
     * 刷新会话令牌。
     * <p>校验 refreshToken 未过期后轮换 access/refresh token 与过期时间。</p>
     *
     * @param command 刷新令牌命令
     * @param traceId 请求链路追踪 ID
     * @return 新的 accessToken、refreshToken 及过期时间
     */
    public Map<String, Object> refresh(RefreshCommand command, String traceId) {
        log.info("刷新令牌请求");
        IamSession session = iamGateway.findSessionByRefreshToken(command.refreshToken());
        if (session == null || session.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("刷新令牌失败: reason=invalid_or_expired");
            throw com.penmate.backend.application.common.exception.BusinessException.of("Refresh token invalid or expired");
        }
        session.setAccessToken("atk_" + UUID.randomUUID());
        session.setRefreshToken("rtk_" + UUID.randomUUID());
        session.setAccessExpiresAt(LocalDateTime.now().plusHours(2));
        session.setRefreshExpiresAt(LocalDateTime.now().plusDays(7));
        if (iamGateway.rotateSession(session) != 1) {
            log.error("刷新令牌失败: userId={}, sessionId={}, reason=rotate_failed", session.getUserId(), session.getId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to refresh token");
        }
        writeAudit(traceId, session.getUserId(), "auth", "refresh", "iam_user_sessions", String.valueOf(session.getId()), null, 200);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", session.getAccessToken());
        result.put("refreshToken", session.getRefreshToken());
        result.put("accessExpiresAt", session.getAccessExpiresAt());
        result.put("refreshExpiresAt", session.getRefreshExpiresAt());
        log.info("刷新令牌成功: userId={}, sessionId={}", session.getUserId(), session.getId());
        return result;
    }

    /**
     * 查询当前登录用户信息。
     * <p>根据 accessToken 获取会话、用户、角色与权限集合。</p>
     *
     * @param authorization Authorization 头中的 Bearer Token
     * @return 当前用户基础信息、角色列表与权限列表
     */
    public Map<String, Object> me(String authorization) {
        String token = extractBearer(authorization);
        IamSession session = iamGateway.findSessionByAccessToken(token);
        if (session == null || session.getAccessExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("查询当前用户失败: reason=login_required");
            throw com.penmate.backend.application.common.exception.BusinessException.of("Login required");
        }
        IamUser user = iamGateway.findUserById(session.getUserId());
        if (user == null) {
            log.warn("查询当前用户失败: userId={}, reason=user_not_found", session.getUserId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        List<IamRole> roles = iamGateway.findRolesByUserId(user.getId());
        List<IamPermission> permissions = iamGateway.findPermissionsByUserId(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("email", user.getEmail());
        result.put("displayName", user.getDisplayName());
        result.put("roles", roles);
        result.put("permissions", permissions);
        log.info("查询当前用户成功: userId={}, roleCount={}, permissionCount={}", user.getId(), roles.size(), permissions.size());
        return result;
    }

    private String extractBearer(String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Authorization header missing Bearer token");
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

