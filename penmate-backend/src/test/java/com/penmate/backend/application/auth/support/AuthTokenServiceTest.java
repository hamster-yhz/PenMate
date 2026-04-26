package com.penmate.backend.application.auth.support;

import com.penmate.backend.application.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthTokenServiceTest {

    private final AuthTokenService tokenService = new AuthTokenService(
            "01234567890123456789012345678901",
            "penmate-test",
            30,
            7
    );

    @Test
    void should_throw_specific_error_when_parsing_access_token_with_blank_input() {
        assertThatThrownBy(() -> tokenService.parseAccessToken("   "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token is blank");
    }

    @Test
    void should_parse_access_token_when_input_has_leading_and_trailing_spaces() {
        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(3001L);
        AuthTokenBundle bundle = tokenService.issueTokens(payload);

        ParsedToken parsed = tokenService.parseAccessToken("  " + bundle.accessToken() + "  ");

        assertThat(parsed.userId()).isEqualTo(3001L);
        assertThat(parsed.tokenType()).isEqualTo("ACCESS");
        assertThat(parsed.tokenId()).isEqualTo(bundle.accessJti());
    }

    @Test
    void should_parse_refresh_token_when_input_has_leading_and_trailing_spaces() {
        AuthUserSessionPayload payload = new AuthUserSessionPayload();
        payload.setUserId(3002L);
        AuthTokenBundle bundle = tokenService.issueTokens(payload);

        ParsedToken parsed = tokenService.parseRefreshToken("  " + bundle.refreshToken() + "  ");

        assertThat(parsed.userId()).isEqualTo(3002L);
        assertThat(parsed.tokenType()).isEqualTo("REFRESH");
        assertThat(parsed.tokenId()).isEqualTo(bundle.refreshJti());
    }
}

