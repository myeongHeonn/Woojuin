package com.ssafy.woojuin.domain.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 기본 실패 핸들러는 백엔드 자신의 "/login"으로 리다이렉트하는데, 프론트가 분리된
 * 구조라 백엔드엔 그 경로가 없어 404("No static resource login.")가 난다.
 * 성공 핸들러와 같은 콜백 경로로 보내면, 토큰 파라미터가 없을 때 OAuthCallbackPage가
 * 이미 "/login"으로 보내주므로 프론트 쪽 분기는 그대로 재사용된다.
 *
 * <p><b>실패 사유를 함께 싣는다.</b> 예전에는 예외를 버리고 콜백 경로로만 보냈는데, 그러면
 * 프론트는 토큰이 없다는 것만 알고 조용히 로그인 화면으로 되돌린다 — 사용자 눈에는 "구글 버튼을
 * 눌렀는데 아무 일도 없음"이 되고, 다시 눌러도 같은 자리를 돈다. 사유를 쿼리로 넘겨 로그인
 * 화면이 무슨 일이 있었는지 말할 수 있게 한다.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    /** OAuth2 규격 오류코드가 아닌 실패(네트워크 등)에 쓸 기본값. 프론트가 문구로 옮긴다. */
    private static final String DEFAULT_ERROR_CODE = "oauth_failed";

    private final String redirectBaseUrl;

    public OAuth2LoginFailureHandler(@Value("${woojuin.oauth.redirect-base-url}") String redirectBaseUrl) {
        this.redirectBaseUrl = redirectBaseUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        response.sendRedirect(redirectBaseUrl + separator() + "error=" + errorCode(exception));
    }

    /**
     * 오류코드는 프론트가 문구로 옮길 <b>키</b>로만 쓴다 — 서버 메시지를 그대로 내보내지 않는 이유는
     * 문구 결정권을 프론트에 두고(다국어·톤) 예외 메시지가 사용자 화면에 새지 않게 하려는 것이다.
     *
     * <p>{@code withdrawn_user} 는 OAuthAccountService 가, {@code access_denied} 등은 구글이 준다
     * (사용자가 동의 화면에서 취소하면 이 값이다 — 프론트는 이 경우 오류를 띄우지 않는다).
     */
    private String errorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            String code = oauth2Exception.getError().getErrorCode();
            if (code != null && !code.isBlank()) {
                return URLEncoder.encode(code, StandardCharsets.UTF_8);
            }
        }
        return DEFAULT_ERROR_CODE;
    }

    /** redirect-base-url 에 이미 쿼리가 붙어 있을 수 있다(운영 설정에서 바꿀 수 있는 값이다). */
    private String separator() {
        return redirectBaseUrl.contains("?") ? "&" : "?";
    }
}
