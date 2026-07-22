package com.penmate.backend.application.auth.support;

public interface AuthSessionCache {

    void saveSession(AuthUserSessionPayload payload, AuthTokenBundle bundle);

    AuthUserSessionPayload getByAccessJti(String accessJti);

    AuthUserSessionPayload getByRefreshJti(String refreshJti);

    void revokeAccess(String accessJti);

    void revokeRefresh(String refreshJti);

    void revokeRefreshFingerprint(String refreshJtiHash);

    void updateSessionPayload(String accessJti, AuthUserSessionPayload payload);
}
