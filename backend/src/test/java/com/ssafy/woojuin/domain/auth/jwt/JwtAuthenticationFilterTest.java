package com.ssafy.woojuin.domain.auth.jwt;

import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.auth.session.SessionRevocationStore;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRevocationStore revocationStore;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenProvider, userRepository, revocationStore);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이면 SecurityContext에 userId가 설정되고 체인이 진행된다")
    void validToken_setsAuthenticationAndContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getSessionId("valid-token")).thenReturn("sid-1");
        when(revocationStore.isRevoked("sid-1")).thenReturn(false);
        when(tokenProvider.getUserId("valid-token")).thenReturn(1L);
        when(userRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
        // sid 는 details 로 실려 로그아웃·기기 목록의 "이 기기" 판별에 쓰인다
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails()).isEqualTo("sid-1");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("폐기된 세션의 토큰은 서명이 유효해도 인증하지 않는다 — 기기 해제가 즉시 반영되는 지점")
    void revokedSessionToken_doesNotAuthenticate_butContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer revoked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("revoked-token")).thenReturn(true);
        when(tokenProvider.getSessionId("revoked-token")).thenReturn("sid-x");
        when(revocationStore.isRevoked("sid-x")).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // 폐기 확인이 DB 확인보다 앞이다 — 해제된 기기의 요청에 Postgres 조회를 쓰지 않는다
        verify(userRepository, never()).existsByIdAndDeletedAtIsNull(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("sid 가 없는(세션 구조 배포 전) 토큰은 폐기 확인 없이 통과한다 — 남은 수명 최대 1시간")
    void legacyTokenWithoutSid_authenticates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer legacy-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("legacy-token")).thenReturn(true);
        when(tokenProvider.getSessionId("legacy-token")).thenReturn(null);
        when(tokenProvider.getUserId("legacy-token")).thenReturn(1L);
        when(userRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(revocationStore, never()).isRevoked(any());
    }

    @Test
    @DisplayName("탈퇴 사용자의 유효한 토큰은 인증하지 않고 체인만 진행한다")
    void withdrawnUserToken_doesNotAuthenticate_butContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer withdrawn-user-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("withdrawn-user-token")).thenReturn(true);
        when(tokenProvider.getSessionId("withdrawn-user-token")).thenReturn("sid-1");
        when(revocationStore.isRevoked("sid-1")).thenReturn(false);
        when(tokenProvider.getUserId("withdrawn-user-token")).thenReturn(1L);
        when(userRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 SecurityContext를 건드리지 않고 체인만 진행된다")
    void noAuthorizationHeader_doesNotAuthenticate_butContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 SecurityContext가 비어있는 채 체인만 진행된다")
    void invalidToken_doesNotAuthenticate_butContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("invalid-token")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
