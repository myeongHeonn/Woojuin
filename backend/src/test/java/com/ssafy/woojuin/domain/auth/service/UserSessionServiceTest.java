package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.dto.SessionResponse;
import com.ssafy.woojuin.domain.auth.session.SessionRevocationStore;
import com.ssafy.woojuin.domain.auth.session.UserSession;
import com.ssafy.woojuin.domain.auth.session.UserSessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    @Mock
    private UserSessionStore sessionStore;

    @Mock
    private SessionRevocationStore revocationStore;

    @InjectMocks
    private UserSessionService sessionService;

    @Test
    @DisplayName("목록은 마지막 사용이 최근인 것부터, 호출한 기기에 current 가 붙는다")
    void list_sortsByLastUsedDesc_marksCurrent() {
        UserSession older = new UserSession("sid-old", "rt-1",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0.0.0 Safari/537.36", 1_000L, 1_000L);
        UserSession newer = new UserSession("sid-new", "rt-2",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) Version/17.5 Mobile/15E148 Safari/604.1",
                2_000L, 2_000L);
        when(sessionStore.list(1L)).thenReturn(List.of(older, newer));

        List<SessionResponse> sessions = sessionService.list(1L, "sid-new");

        assertThat(sessions).extracting(SessionResponse::sessionId)
                .containsExactly("sid-new", "sid-old");
        assertThat(sessions).extracting(SessionResponse::current)
                .containsExactly(true, false);
        assertThat(sessions.get(0).deviceName()).isEqualTo("iPhone · Safari");
    }

    @Test
    @DisplayName("기기 해제는 세션 삭제(refresh 차단)와 폐기 등록(access 즉시 차단)을 함께 한다")
    void revoke_deletesSessionAndRevokes() {
        when(sessionStore.find(1L, "sid-1"))
                .thenReturn(Optional.of(UserSession.start("sid-1", "rt", "Mozilla/5.0")));

        sessionService.revoke(1L, "sid-1");

        verify(sessionStore).delete(1L, "sid-1");
        verify(revocationStore).revoke("sid-1");
    }

    @Test
    @DisplayName("내 세션이 아니면 폐기하지 않는다 — 남의 sid 를 골라 차단하는 통로를 막는다")
    void revoke_sessionNotMine_throwsWithoutRevoking() {
        when(sessionStore.find(1L, "sid-of-someone-else")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.revoke(1L, "sid-of-someone-else"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(sessionStore, never()).delete(1L, "sid-of-someone-else");
        verify(revocationStore, never()).revoke("sid-of-someone-else");
    }

    @Test
    @DisplayName("전체 해제는 지워진 모든 sid 를 폐기 목록에 올린다 — 모든 기기가 즉시 끊긴다")
    void revokeAll_revokesEveryRemovedSid() {
        when(sessionStore.deleteAll(1L)).thenReturn(List.of("sid-1", "sid-2", "sid-3"));

        sessionService.revokeAll(1L);

        verify(revocationStore).revoke("sid-1");
        verify(revocationStore).revoke("sid-2");
        verify(revocationStore).revoke("sid-3");
    }
}
