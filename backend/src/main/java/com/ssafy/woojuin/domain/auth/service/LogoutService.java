package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.session.SessionRevocationStore;
import com.ssafy.woojuin.domain.auth.session.UserSessionStore;
import org.springframework.stereotype.Service;

@Service
public class LogoutService {

    private final UserSessionStore sessionStore;
    private final SessionRevocationStore revocationStore;

    public LogoutService(UserSessionStore sessionStore, SessionRevocationStore revocationStore) {
        this.sessionStore = sessionStore;
        this.revocationStore = revocationStore;
    }

    /**
     * 로그아웃은 **그 기기만** 끊는다 — 세션을 지워 refresh 를 막고, 폐기 목록에 올려
     * 아직 살아 있는 access token 도 즉시 막는다. 다른 기기는 각자의 세션이라 영향이 없다.
     *
     * @param sessionId null 이면 세션 구조 배포 전 토큰이다 — 지울 세션이 없으므로 아무
     *                  일도 하지 않는다(그 refresh token 은 어차피 세션 조회에서 거부된다)
     */
    public void logout(Long userId, String sessionId) {
        if (sessionId == null) return;
        sessionStore.delete(userId, sessionId);
        revocationStore.revoke(sessionId);
    }
}
