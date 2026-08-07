package com.ssafy.woojuin.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * 실패 사유가 프론트까지 도달하는지 본다. 사유를 버리면 프론트는 토큰이 없다는 것만 알고
 * 조용히 로그인 화면으로 되돌리는데, 사용자 눈에는 "구글 버튼을 눌렀는데 아무 일도 없음"이 된다.
 */
class OAuth2LoginFailureHandlerTest {

    private static final String CALLBACK = "http://localhost:5173/oauth/callback";

    @Test
    @DisplayName("OAuth2 오류코드를 error 파라미터로 실어 콜백 경로로 보낸다")
    void redirectsWithOAuth2ErrorCode() throws IOException {
        MockHttpServletResponse response = redirect(CALLBACK,
                new OAuth2AuthenticationException(new OAuth2Error("withdrawn_user"), "탈퇴한 계정입니다."));

        assertThat(response.getRedirectedUrl())
                .isEqualTo(CALLBACK + "?error=withdrawn_user");
    }

    @Test
    @DisplayName("OAuth2 규격 예외가 아니면 기본 오류코드로 떨어진다")
    void redirectsWithDefaultErrorCodeForNonOAuth2Failures() throws IOException {
        MockHttpServletResponse response = redirect(CALLBACK, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo(CALLBACK + "?error=oauth_failed");
    }

    @Test
    @DisplayName("redirect-base-url 에 이미 쿼리가 있으면 & 로 이어 붙인다")
    void appendsWithAmpersandWhenBaseUrlAlreadyHasQuery() throws IOException {
        String baseWithQuery = CALLBACK + "?from=landing";

        MockHttpServletResponse response = redirect(baseWithQuery,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "취소"));

        assertThat(response.getRedirectedUrl()).isEqualTo(baseWithQuery + "&error=access_denied");
    }

    @Test
    @DisplayName("오류코드에 URL 특수문자가 섞여도 쿼리스트링을 깨지 않는다")
    void encodesErrorCode() throws IOException {
        MockHttpServletResponse response = redirect(CALLBACK,
                new OAuth2AuthenticationException(new OAuth2Error("weird code&x=1"), "이상한 코드"));

        assertThat(response.getRedirectedUrl()).isEqualTo(CALLBACK + "?error=weird+code%26x%3D1");
    }

    private MockHttpServletResponse redirect(String baseUrl, AuthenticationException exception)
            throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new OAuth2LoginFailureHandler(baseUrl)
                .onAuthenticationFailure(new MockHttpServletRequest(), response, exception);
        return response;
    }
}
