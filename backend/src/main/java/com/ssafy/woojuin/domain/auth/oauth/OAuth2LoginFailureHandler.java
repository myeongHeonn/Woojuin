package com.ssafy.woojuin.domain.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 기본 실패 핸들러는 백엔드 자신의 "/login"으로 리다이렉트하는데, 프론트가 분리된
 * 구조라 백엔드엔 그 경로가 없어 404("No static resource login.")가 난다.
 * 성공 핸들러와 같은 콜백 경로로 보내면, 토큰 파라미터가 없을 때 OAuthCallbackPage가
 * 이미 "/login"으로 보내주므로 프론트 쪽 분기는 그대로 재사용된다.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final String redirectBaseUrl;

    public OAuth2LoginFailureHandler(@Value("${woojuin.oauth.redirect-base-url}") String redirectBaseUrl) {
        this.redirectBaseUrl = redirectBaseUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        response.sendRedirect(redirectBaseUrl);
    }
}
