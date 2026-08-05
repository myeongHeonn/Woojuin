package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.dto.TokenResponse;
import com.ssafy.woojuin.domain.auth.jwt.JwtTokenProvider;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.auth.session.UserSession;
import com.ssafy.woojuin.domain.auth.session.UserSessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRefreshServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserSessionStore sessionStore;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TokenRefreshService tokenRefreshService;

    @Test
    @DisplayName("유효한 refresh 면 같은 sid 의 새 access 를 주고, refresh 는 회전하지 않는다")
    void refresh_returnsNewAccess_keepsSameRefresh() {
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getSessionId("refresh-token")).thenReturn("sid-1");
        when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(1L);
        when(sessionStore.find(1L, "sid-1")).thenReturn(Optional.of(session("refresh-token")));
        when(userRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L, "sid-1")).thenReturn("new-access-token");

        TokenResponse response = tokenRefreshService.refresh("refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        // 회전 없음 — 모바일 복귀 시 응답 유실로 정상 사용자가 로그아웃되는 것을 피한다
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(jwtTokenProvider, never()).createRefreshToken(1L, "sid-1");
        // 마지막 사용 시각 갱신 — 기기 목록의 "마지막 사용" 표시가 여기서 나온다
        verify(sessionStore).touch(1L, "sid-1");
    }

    @Test
    @DisplayName("서명이 깨졌거나 만료된 토큰은 거부한다")
    void refresh_invalidToken_throws() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> tokenRefreshService.refresh("bad-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sid 가 없는(세션 구조 배포 전) refresh 는 거부한다 — 전원 1회 재로그인 결정")
    void refresh_legacyTokenWithoutSid_throws() {
        when(jwtTokenProvider.validateToken("legacy-refresh")).thenReturn(true);
        when(jwtTokenProvider.getSessionId("legacy-refresh")).thenReturn(null);

        assertThatThrownBy(() -> tokenRefreshService.refresh("legacy-refresh"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("저장된 세션이 없으면(로그아웃·기기 해제 후) 거부한다")
    void refresh_noStoredSession_throws() {
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getSessionId("refresh-token")).thenReturn("sid-1");
        when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(1L);
        when(sessionStore.find(1L, "sid-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenRefreshService.refresh("refresh-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("저장된 refresh 와 다른 토큰이면 거부한다")
    void refresh_tokenMismatch_throws() {
        when(jwtTokenProvider.validateToken("stale-refresh")).thenReturn(true);
        when(jwtTokenProvider.getSessionId("stale-refresh")).thenReturn("sid-1");
        when(jwtTokenProvider.getUserId("stale-refresh")).thenReturn(1L);
        when(sessionStore.find(1L, "sid-1")).thenReturn(Optional.of(session("current-refresh")));

        assertThatThrownBy(() -> tokenRefreshService.refresh("stale-refresh"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sessionStore, never()).touch(1L, "sid-1");
    }

    @Test
    @DisplayName("탈퇴한 사용자의 refresh 는 거부한다")
    void refresh_withdrawnUser_throws() {
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getSessionId("refresh-token")).thenReturn("sid-1");
        when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(1L);
        when(sessionStore.find(1L, "sid-1")).thenReturn(Optional.of(session("refresh-token")));
        when(userRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(false);

        assertThatThrownBy(() -> tokenRefreshService.refresh("refresh-token"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sessionStore, never()).touch(1L, "sid-1");
    }

    private UserSession session(String refreshToken) {
        return UserSession.start("sid-1", refreshToken, "Mozilla/5.0");
    }
}
