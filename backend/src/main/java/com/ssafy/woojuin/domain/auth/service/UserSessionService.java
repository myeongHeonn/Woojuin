package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.dto.SessionResponse;
import com.ssafy.woojuin.domain.auth.session.SessionRevocationStore;
import com.ssafy.woojuin.domain.auth.session.UserSession;
import com.ssafy.woojuin.domain.auth.session.UserSessionStore;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 기기 관리 — 로그인된 세션의 목록·개별 해제·전체 해제 (S15P11C105-459/-460).
 */
@Service
public class UserSessionService {

    private final UserSessionStore sessionStore;
    private final SessionRevocationStore revocationStore;

    public UserSessionService(UserSessionStore sessionStore, SessionRevocationStore revocationStore) {
        this.sessionStore = sessionStore;
        this.revocationStore = revocationStore;
    }

    /** 마지막 사용이 최근인 기기부터 — 지금 쓰는 기기가 자연스럽게 위로 온다 */
    public List<SessionResponse> list(Long userId, String currentSid) {
        return sessionStore.list(userId).stream()
                .sorted(Comparator.comparingLong(UserSession::lastUsedAt).reversed())
                .map(session -> SessionResponse.from(session, currentSid))
                .toList();
    }

    /**
     * 특정 기기(세션)를 해제한다 — 세션 삭제(refresh 차단) + 폐기 등록(access 즉시 차단).
     *
     * **내 세션인지 먼저 확인하고 폐기한다.** 이 확인이 없으면 아무 sid 문자열이나 넣어
     * 폐기 목록에 올릴 수 있다 — sid 를 알아내거나 추측한 공격자가 남의 기기를 골라
     * 차단하는 통로가 된다. 내 해시에 있는 sid 만 폐기하면 그 통로가 없다.
     */
    public void revoke(Long userId, String sid) {
        sessionStore.find(userId, sid)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다"));
        sessionStore.delete(userId, sid);
        revocationStore.revoke(sid);
    }

    /**
     * 모든 기기에서 로그아웃 — 비밀번호 유출 대비 기능이라 **즉시** 들어야 한다.
     * 지금 쓰는 기기도 함께 끊기므로 호출한 클라이언트는 응답을 받으면 로그인 화면으로 간다.
     */
    public void revokeAll(Long userId) {
        sessionStore.deleteAll(userId).forEach(revocationStore::revoke);
    }
}
