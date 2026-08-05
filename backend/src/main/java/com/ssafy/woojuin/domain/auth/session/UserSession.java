package com.ssafy.woojuin.domain.auth.session;

import java.util.UUID;

/**
 * 로그인 세션 하나 = 기기 하나. Redis 해시 `sessions:{userId}` 의 필드 값으로 JSON 직렬화된다.
 *
 * 시각을 epoch millis(long) 로 두는 이유: OffsetDateTime 을 JSON 으로 넣으면 JavaTimeModule
 * 구성에 직렬화 모양이 좌우된다 — 저장소에 남는 값은 구성 변화에 흔들리지 않는 원시값이 안전하다.
 * 화면에 보여줄 때만 DTO 에서 시각 타입으로 바꾼다(SessionResponse).
 */
public record UserSession(
        String sid,
        String refreshToken,
        String userAgent,
        long createdAt,
        long lastUsedAt) {

    /** 새 세션 id — 무작위(UUID)라 같은 초에 두 번 로그인해도 서로 다른 세션이 된다 */
    public static String newSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 로그인 시점에 새 세션을 만든다. sid 를 밖에서 받는 이유: refresh token 에 같은 sid 가
     * 실려야 해서, 호출자가 sid → 토큰 → 세션 순서로 만들어야 한다.
     */
    public static UserSession start(String sid, String refreshToken, String userAgent) {
        long now = System.currentTimeMillis();
        return new UserSession(sid, refreshToken, userAgent, now, now);
    }

    public UserSession touched() {
        return new UserSession(sid, refreshToken, userAgent, createdAt, System.currentTimeMillis());
    }

    /** 세션 수명은 refresh token 수명과 같다 — 그 뒤에는 refresh 가 어차피 JWT exp 에서 거부된다 */
    public boolean isExpired(long refreshTokenValidityMillis) {
        return createdAt + refreshTokenValidityMillis <= System.currentTimeMillis();
    }
}
