package com.ssafy.woojuin.domain.auth.oauth;

import com.ssafy.woojuin.domain.auth.jwt.JwtTokenProvider;
import com.ssafy.woojuin.domain.auth.session.UserSession;
import com.ssafy.woojuin.domain.auth.session.UserSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실사용자는 전부 이 경로로 로그인한다(웹은 구글 OAuth 뿐) — LoginService 와 같은
 * "로그인 = 세션 추가" 규칙을 지키는지가 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    private static final String UA_WIN =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserSessionStore sessionStore;

    @Mock
    private Authentication authentication;

    @Mock
    private CustomOidcUser principal;

    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(
                jwtTokenProvider, sessionStore, "http://localhost:5173/oauth/callback");
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getUserId()).thenReturn(1L);
        when(jwtTokenProvider.createAccessToken(eq(1L), anyString())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(eq(1L), anyString())).thenReturn("refresh-token");
    }

    @Test
    @DisplayName("OAuth 로그인도 새 세션을 만들어 저장한다 — 다른 기기의 로그인이 유지된다")
    void success_savesNewSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", UA_WIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(sessionStore).save(eq(1L), saved.capture());
        assertThat(saved.getValue().refreshToken()).isEqualTo("refresh-token");
        assertThat(saved.getValue().userAgent()).isEqualTo(UA_WIN);

        // 토큰에 실린 sid 와 저장된 세션의 sid 가 같아야 refresh·해제가 이 세션을 찾는다
        ArgumentCaptor<String> accessSid = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refreshSid = ArgumentCaptor.forClass(String.class);
        verify(jwtTokenProvider).createAccessToken(eq(1L), accessSid.capture());
        verify(jwtTokenProvider).createRefreshToken(eq(1L), refreshSid.capture());
        assertThat(accessSid.getValue())
                .isEqualTo(refreshSid.getValue())
                .isEqualTo(saved.getValue().sid());
    }

    @Test
    @DisplayName("발급된 두 토큰을 콜백 URL 쿼리로 실어 리다이렉트한다")
    void success_redirectsWithTokens() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", UA_WIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost:5173/oauth/callback")
                .contains("accessToken=access-token")
                .contains("refreshToken=refresh-token");
    }

    @Test
    @DisplayName("신규 가입이면 리다이렉트 URL에 isNewUser=true를 실어 보낸다 — 프론트가 온보딩으로 보낸다")
    void success_newUser_redirectsWithIsNewUserTrue() throws Exception {
        when(principal.isNewUser()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).contains("isNewUser=true");
    }

    @Test
    @DisplayName("기존 가입자면 리다이렉트 URL에 isNewUser=false를 실어 보낸다")
    void success_existingUser_redirectsWithIsNewUserFalse() throws Exception {
        when(principal.isNewUser()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).contains("isNewUser=false");
    }

    @Test
    @DisplayName("User-Agent 가 없어도 로그인은 된다 — 이름만 '알 수 없는 기기'가 된다")
    void success_withoutUserAgent_stillSavesSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(sessionStore).save(eq(1L), saved.capture());
        assertThat(saved.getValue().userAgent()).isNull();
        assertThat(response.getRedirectedUrl()).contains("accessToken=access-token");
    }
}
