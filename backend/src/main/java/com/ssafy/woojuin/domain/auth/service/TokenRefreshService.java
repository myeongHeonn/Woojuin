package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.dto.TokenResponse;
import com.ssafy.woojuin.domain.auth.jwt.JwtTokenProvider;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.auth.session.UserSession;
import com.ssafy.woojuin.domain.auth.session.UserSessionStore;
import org.springframework.stereotype.Service;

@Service
public class TokenRefreshService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserSessionStore sessionStore;
    private final UserRepository userRepository;

    public TokenRefreshService(JwtTokenProvider jwtTokenProvider, UserSessionStore sessionStore,
                               UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionStore = sessionStore;
        this.userRepository = userRepository;
    }

    /*
     * 거부는 전부 IllegalArgumentException → GlobalExceptionHandler → **400** 이다.
     * 이 상태 코드는 프론트와의 계약이다 — services/client.ts 의 isRefreshRejected 가
     * [400, 401, 403] 만 "서버가 거부했다"로 보고 토큰을 지워 로그인 화면으로 보낸다
     * (S15P11C105-455). 다른 코드(404 등)로 바꾸면 해제된 기기가 통신 실패로 오인돼
     * 로그인 화면으로 못 가고 갇힌다.
     *
     * **회전(rotation)은 없다** — 받은 refresh token 을 그대로 돌려준다. 매번 갈아치우면
     * 서버는 갈았는데 응답이 유실된 경우(모바일 복귀 시점에 잦다) 클라이언트가 옛 토큰을
     * 들고 남아 다음 refresh 가 정당한 400 이 된다. 넣으려면 직전 토큰을 잠시 허용하는
     * leeway 가 같이 필요해서 이번 범위에서 뺐다(S15P11C105-459 의 결정).
     */
    public TokenResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 refresh token입니다");
        }

        String sid = jwtTokenProvider.getSessionId(refreshToken);
        if (sid == null) {
            // 세션 구조 배포 전에 발급된 토큰 — 전원 1회 재로그인을 감수하기로 했다
            throw new IllegalArgumentException("세션 정보가 없는 refresh token입니다");
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        UserSession session = sessionStore.find(userId, sid)
                .orElseThrow(() -> new IllegalArgumentException("저장된 세션이 없습니다"));
        if (!session.refreshToken().equals(refreshToken)) {
            throw new IllegalArgumentException("refresh token이 일치하지 않습니다");
        }

        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new IllegalArgumentException("탈퇴했거나 존재하지 않는 사용자입니다.");
        }

        // 기기 목록의 "마지막 사용" — refresh 가 곧 그 기기가 살아 있다는 신호다
        sessionStore.touch(userId, sid);

        String newAccessToken = jwtTokenProvider.createAccessToken(userId, sid);
        return new TokenResponse(newAccessToken, refreshToken);
    }
}
