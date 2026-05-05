package com.penmate.backend.application.auth;

import com.penmate.backend.application.auth.command.LoginCommand;
import com.penmate.backend.application.auth.command.RefreshCommand;
import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.auth.support.AuthTokenBundle;
import com.penmate.backend.application.auth.support.AuthTokenService;
import com.penmate.backend.application.auth.support.AuthUserSessionPayload;
import com.penmate.backend.application.auth.support.ParsedToken;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证应用服务。
 * <p>负责登录、登出、令牌刷新与当前登录用户信息查询，并在关键认证动作后记录审计日志。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthApplicationService {

    private final IamGateway iamGateway;
    private final AuthTokenService authTokenService;
    private final AuthSessionCache authSessionCache;

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

        List<IamRole> roles = iamGateway.findRolesByUserId(user.getId());
        List<IamPermission> permissions = iamGateway.findPermissionsByUserId(user.getId());

        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(user.getId());
        payload.setEmail(user.getEmail());
        payload.setDisplayName(user.getDisplayName());
        payload.setStatus(user.getStatus());
        payload.setRoles(toRoleMaps(roles));
        payload.setPermissions(toPermissionMaps(permissions));
        payload.setMainAgentModelConfigId(user.getMainAgentModelConfigId());
        payload.setDirtyWorkAgentModelConfigId(user.getDirtyWorkAgentModelConfigId());

        AuthTokenBundle bundle = authTokenService.issueTokens(payload);
        payload.setRefreshJti(bundle.refreshJti());
        authSessionCache.saveSession(payload, bundle);
        iamGateway.touchLastLogin(user.getId());

        writeAudit(traceId, user.getId(), "auth", "login", "redis_auth_tokens", bundle.accessJti(), command.email(), 200);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", bundle.accessToken());
        result.put("refreshToken", bundle.refreshToken());
        result.put("accessExpiresAt", bundle.accessExpiresAt());
        result.put("refreshExpiresAt", bundle.refreshExpiresAt());
        log.info("登录成功: userId={}, accessJti={}", user.getId(), bundle.accessJti());
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
        ParsedToken parsed = authTokenService.parseAccessToken(token);
        AuthUserSessionPayload payload = authSessionCache.getByAccessJti(parsed.tokenId());
        if (payload == null) {
            log.info("登出请求忽略: reason=session_not_found");
            return;
        }
        authSessionCache.revokeAccess(parsed.tokenId());
        if (payload.getRefreshJti() != null && !payload.getRefreshJti().isBlank()) {
            authSessionCache.revokeRefresh(payload.getRefreshJti());
        }
        writeAudit(traceId, parsed.userId(), "auth", "logout", "redis_auth_tokens", parsed.tokenId(), null, 200);
        log.info("登出成功: userId={}, accessJti={}", parsed.userId(), parsed.tokenId());
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
        ParsedToken parsed = authTokenService.parseRefreshToken(command.refreshToken());
        AuthUserSessionPayload payload = authSessionCache.getByRefreshJti(parsed.tokenId());
        if (payload == null) {
            log.warn("刷新令牌失败: reason=invalid_or_expired");
            throw com.penmate.backend.application.common.exception.BusinessException.of("Refresh token invalid or expired");
        }
        authSessionCache.revokeRefresh(parsed.tokenId());
        AuthTokenBundle bundle = authTokenService.issueTokens(payload);
        payload.setRefreshJti(bundle.refreshJti());
        authSessionCache.saveSession(payload, bundle);

        writeAudit(traceId, payload.getUserId(), "auth", "refresh", "redis_auth_tokens", bundle.refreshJti(), null, 200);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", bundle.accessToken());
        result.put("refreshToken", bundle.refreshToken());
        result.put("accessExpiresAt", bundle.accessExpiresAt());
        result.put("refreshExpiresAt", bundle.refreshExpiresAt());
        log.info("刷新令牌成功: userId={}, refreshJti={}", payload.getUserId(), bundle.refreshJti());
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
        ParsedToken parsed = authTokenService.parseAccessToken(token);
        AuthUserSessionPayload payload = authSessionCache.getByAccessJti(parsed.tokenId());
        if (payload == null) {
            log.warn("查询当前用户失败: reason=login_required");
            throw com.penmate.backend.application.common.exception.BusinessException.of("Login required");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", payload.getUserId());
        result.put("email", payload.getEmail());
        result.put("displayName", payload.getDisplayName());
        result.put("roles", payload.getRoles());
        result.put("permissions", payload.getPermissions());
        log.info("查询当前用户成功: userId={}, roleCount={}, permissionCount={}",
                payload.getUserId(),
                payload.getRoles() == null ? 0 : payload.getRoles().size(),
                payload.getPermissions() == null ? 0 : payload.getPermissions().size());
        return result;
    }

    private String extractBearer(String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Authorization header missing Bearer token");
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private List<Map<String, Object>> toRoleMaps(List<IamRole> roles) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (IamRole role : roles) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", role.getId());
            item.put("name", role.getName());
            item.put("code", role.getCode());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> toPermissionMaps(List<IamPermission> permissions) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (IamPermission permission : permissions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", permission.getId());
            item.put("name", permission.getName());
            item.put("code", permission.getCode());
            item.put("module", permission.getModule());
            result.add(item);
        }
        return result;
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        // 审计模块已移除
    }
}

