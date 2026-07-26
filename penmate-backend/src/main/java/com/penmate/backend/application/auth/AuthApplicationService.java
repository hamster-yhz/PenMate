package com.penmate.backend.application.auth;

import com.penmate.backend.application.auth.command.LoginCommand;
import com.penmate.backend.application.auth.command.RefreshCommand;
import com.penmate.backend.application.auth.support.AuthSessionCache;
import com.penmate.backend.application.auth.support.AuthTokenBundle;
import com.penmate.backend.application.auth.support.AuthTokenFingerprint;
import com.penmate.backend.application.auth.support.AuthTokenService;
import com.penmate.backend.application.auth.support.AuthUserSessionPayload;
import com.penmate.backend.application.auth.support.ParsedToken;
import com.penmate.backend.application.auth.support.UserAgentSummary;
import com.penmate.backend.domain.auth.model.AuthSession;
import com.penmate.backend.domain.auth.model.UserUiPreferences;
import com.penmate.backend.domain.auth.repository.AuthSessionRepository;
import com.penmate.backend.domain.auth.repository.UserUiPreferencesRepository;
import com.penmate.backend.domain.iam.model.IamPermission;
import com.penmate.backend.domain.iam.model.IamRole;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;

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
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionRepository authSessions;
    private final UserUiPreferencesRepository uiPreferences;

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
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            log.warn("登录失败: userId={}, reason=invalid_password", user.getUserId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Invalid credentials");
        }

        Long userId = user.getUserId();
        List<IamRole> roles = iamGateway.findRolesByUserId(userId);
        List<IamPermission> permissions = iamGateway.findPermissionsByUserId(userId);

        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(userId);
        payload.setEmail(user.getEmail());
        payload.setDisplayName(user.getDisplayName());
        payload.setBio(user.getBio());
        payload.setStatus(user.getStatus());
        payload.setAuthorizationVersion(user.getAuthorizationVersion() == null ? 0L : user.getAuthorizationVersion());
        payload.setRoles(toRoleMaps(roles));
        payload.setPermissions(toPermissionMaps(permissions));

        String sessionId = UUID.randomUUID().toString();
        payload.setSessionId(sessionId);

        AuthTokenBundle bundle = authTokenService.issueTokens(payload);
        payload.setRefreshJti(bundle.refreshJti());
        payload.setAccessJti(bundle.accessJti());
        UserAgentSummary userAgent = UserAgentSummary.parse(command.userAgent());
        AuthSession session = new AuthSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setCurrentAccessJti(bundle.accessJti());
        session.setCurrentRefreshJtiHash(AuthTokenFingerprint.sha256(bundle.refreshJti()));
        session.setDeviceName(userAgent.deviceName());
        session.setBrowserName(userAgent.browserName());
        session.setOperatingSystem(userAgent.operatingSystem());
        session.setUserAgent(userAgent.raw());
        session.setIpAddress(normalizeIp(command.ipAddress()));
        session.setRefreshExpiresAt(bundle.refreshExpiresAt());
        if (authSessions.insert(session) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create auth session");
        }
        authSessionCache.saveSession(payload, bundle);
        iamGateway.touchLastLoginByUserId(userId);

        writeAudit(traceId, userId, "auth", "login", "redis_auth_tokens", bundle.accessJti(), command.email(), 200);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", bundle.accessToken());
        result.put("refreshToken", bundle.refreshToken());
        result.put("accessExpiresAt", bundle.accessExpiresAt());
        result.put("refreshExpiresAt", bundle.refreshExpiresAt());
        log.info("登录成功: userId={}, accessJti={}", userId, bundle.accessJti());
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
        if (payload.getSessionId() != null && !payload.getSessionId().isBlank()) {
            authSessions.revoke(payload.getSessionId(), parsed.userId(), Instant.now());
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
        Long currentAuthorizationVersion = iamGateway.findAuthorizationVersion(payload.getUserId());
        if (currentAuthorizationVersion == null
                || !currentAuthorizationVersion.equals(payload.getAuthorizationVersion())) {
            throw com.penmate.backend.application.common.exception.BusinessException.unauthorized(
                    "Authorization changed; sign in again");
        }
        AuthTokenBundle bundle = authTokenService.issueTokens(payload);
        String sessionId = payload.getSessionId();
        if (sessionId == null || authSessions.rotate(sessionId, parsed.userId(),
                AuthTokenFingerprint.sha256(parsed.tokenId()), bundle.accessJti(),
                AuthTokenFingerprint.sha256(bundle.refreshJti()), normalizeIp(command.ipAddress()),
                bundle.refreshExpiresAt(), Instant.now()) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.unauthorized(
                    "Refresh token was already used or the session expired");
        }
        authSessionCache.revokeRefresh(parsed.tokenId());
        if (payload.getAccessJti() != null && !payload.getAccessJti().isBlank()) {
            authSessionCache.revokeAccess(payload.getAccessJti());
        }
        payload.setRefreshJti(bundle.refreshJti());
        payload.setAccessJti(bundle.accessJti());
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
        result.put("bio", payload.getBio());
        result.put("roles", payload.getRoles());
        result.put("permissions", payload.getPermissions());
        log.info("查询当前用户成功: userId={}, roleCount={}, permissionCount={}",
                payload.getUserId(),
                payload.getRoles() == null ? 0 : payload.getRoles().size(),
                payload.getPermissions() == null ? 0 : payload.getPermissions().size());
        return result;
    }

    public Map<String, Object> updateProfile(String authorization, String displayName, String bio) {
        ParsedToken parsed = authTokenService.parseAccessToken(extractBearer(authorization));
        AuthUserSessionPayload payload = authSessionCache.getByAccessJti(parsed.tokenId());
        if (payload == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Login required");
        }
        Long currentAuthorizationVersion = iamGateway.findAuthorizationVersion(payload.getUserId());
        if (currentAuthorizationVersion == null
                || !currentAuthorizationVersion.equals(payload.getAuthorizationVersion())) {
            throw com.penmate.backend.application.common.exception.BusinessException.unauthorized(
                    "Authorization changed; sign in again");
        }

        IamUser user = iamGateway.findUserByUserId(parsed.userId());
        if (user == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("User not found");
        }
        user.setDisplayName(displayName.trim());
        user.setBio(bio == null ? "" : bio.trim());
        if (iamGateway.updateOwnProfile(user) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Profile update failed");
        }

        payload.setDisplayName(user.getDisplayName());
        payload.setBio(user.getBio());
        authSessionCache.updateSessionPayload(parsed.tokenId(), payload);
        return me(authorization);
    }

    public List<AuthSessionView> listSessions(String authorization) {
        ParsedToken parsed = authTokenService.parseAccessToken(extractBearer(authorization));
        AuthUserSessionPayload current = authSessionCache.getByAccessJti(parsed.tokenId());
        if (current == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.unauthorized("Login required");
        }
        return authSessions.listByUser(parsed.userId()).stream()
                .map(session -> new AuthSessionView(
                        session.getSessionId(), session.getDeviceName(), session.getBrowserName(),
                        session.getOperatingSystem(), session.getIpAddress(), session.getCreatedAt(),
                        session.getLastSeenAt(), session.getRefreshExpiresAt(),
                        Objects.equals(session.getSessionId(), current.getSessionId())))
                .toList();
    }

    public void revokeSession(String authorization, String sessionId) {
        ParsedToken parsed = authTokenService.parseAccessToken(extractBearer(authorization));
        AuthUserSessionPayload current = authSessionCache.getByAccessJti(parsed.tokenId());
        if (current == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.unauthorized("Login required");
        }
        if (Objects.equals(current.getSessionId(), sessionId)) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict(
                    "Use logout to end the current session");
        }
        AuthSession target = authSessions.findByIdAndUser(sessionId, parsed.userId());
        if (target == null || target.getRevokedAt() != null) {
            throw com.penmate.backend.application.common.exception.BusinessException.notFound("Auth session not found");
        }
        if (authSessions.revoke(sessionId, parsed.userId(), Instant.now()) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict(
                    "Auth session is no longer active");
        }
        authSessionCache.revokeAccess(target.getCurrentAccessJti());
        authSessionCache.revokeRefreshFingerprint(target.getCurrentRefreshJtiHash());
    }

    @Transactional
    public int revokeOtherSessions(String authorization) {
        ParsedToken parsed = authTokenService.parseAccessToken(extractBearer(authorization));
        AuthUserSessionPayload current = authSessionCache.getByAccessJti(parsed.tokenId());
        if (current == null || current.getSessionId() == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.unauthorized("Login required");
        }
        List<AuthSession> revoked = authSessions.revokeAllExcept(
                parsed.userId(), current.getSessionId(), Instant.now());
        for (AuthSession target : revoked) {
            if (target.getCurrentAccessJti() != null) authSessionCache.revokeAccess(target.getCurrentAccessJti());
            if (target.getCurrentRefreshJtiHash() != null) {
                authSessionCache.revokeRefreshFingerprint(target.getCurrentRefreshJtiHash());
            }
        }
        return revoked.size();
    }

    public UserUiPreferences getUiPreferences(String authorization) {
        Long userId = requireActiveUserId(authorization);
        UserUiPreferences stored = uiPreferences.findByUserId(userId);
        return stored == null ? defaultUiPreferences(userId) : stored;
    }

    public UserUiPreferences saveUiPreferences(String authorization, UserUiPreferences preferences) {
        Long userId = requireActiveUserId(authorization);
        preferences.setId(null);
        preferences.setUserId(userId);
        preferences.setThemeMode(preferences.getThemeMode().trim().toUpperCase(Locale.ROOT));
        preferences.setEditorFontFamily(preferences.getEditorFontFamily().trim().toUpperCase(Locale.ROOT));
        if (uiPreferences.upsert(preferences) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("UI preferences update failed");
        }
        UserUiPreferences saved = uiPreferences.findByUserId(userId);
        return saved == null ? preferences : saved;
    }

    private Long requireActiveUserId(String authorization) {
        ParsedToken parsed = authTokenService.parseAccessToken(extractBearer(authorization));
        if (authSessionCache.getByAccessJti(parsed.tokenId()) == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.unauthorized("Login required");
        }
        return parsed.userId();
    }

    private UserUiPreferences defaultUiPreferences(Long userId) {
        UserUiPreferences defaults = new UserUiPreferences();
        defaults.setUserId(userId);
        defaults.setThemeMode("SYSTEM");
        defaults.setEditorFontFamily("SERIF");
        defaults.setEditorFontSize(17);
        defaults.setEditorLineHeight(new BigDecimal("1.90"));
        defaults.setEditorParagraphSpacing(new BigDecimal("0.35"));
        defaults.setEditorContentWidth(760);
        defaults.setTypewriterMode(false);
        defaults.setHighlightCurrentParagraph(true);
        return defaults;
    }

    private String normalizeIp(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String normalized = value.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    public record AuthSessionView(String sessionId, String deviceName, String browserName,
                                  String operatingSystem, String ipAddress, Instant createdAt,
                                  Instant lastSeenAt, Instant refreshExpiresAt, boolean current) {
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
            item.put("id", role.getRoleId());
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
            item.put("id", permission.getPermissionId());
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

