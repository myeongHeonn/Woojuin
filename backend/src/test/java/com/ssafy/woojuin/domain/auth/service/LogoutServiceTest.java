package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.session.SessionRevocationStore;
import com.ssafy.woojuin.domain.auth.session.UserSessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    @Mock
    private UserSessionStore sessionStore;

    @Mock
    private SessionRevocationStore revocationStore;

    @InjectMocks
    private LogoutService logoutService;

    @Test
    @DisplayName("로그아웃은 그 세션만 지우고 폐기 목록에 올린다 — 다른 기기는 건드리지 않는다")
    void logout_deletesAndRevokesOnlyThatSession() {
        logoutService.logout(1L, "sid-1");

        verify(sessionStore).delete(1L, "sid-1");
        verify(revocationStore).revoke("sid-1");
    }

    @Test
    @DisplayName("sid 가 없는(구버전) 토큰이면 아무 일도 하지 않는다 — 지울 세션이 없다")
    void logout_withoutSessionId_doesNothing() {
        logoutService.logout(1L, null);

        verifyNoInteractions(sessionStore, revocationStore);
    }
}
