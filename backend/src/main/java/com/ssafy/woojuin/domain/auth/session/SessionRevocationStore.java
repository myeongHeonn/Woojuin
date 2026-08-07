package com.ssafy.woojuin.domain.auth.session;

import com.ssafy.woojuin.domain.auth.jwt.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 해제된 세션의 폐기 목록(denylist) — 기기 해제가 **즉시** 반영되게 하는 장치.
 *
 * 세션을 지우는 것만으로는 그 기기의 access token 이 만료될 때까지(최대 1시간) 계속
 * 통과한다 — JwtAuthenticationFilter 는 서명만 보기 때문이다. "모든 기기에서 로그아웃"이
 * 한 시간 늦게 듣는다면 존재 이유("지금 당장 차단")가 없다. 그래서 해제된 sid 를 여기
 * 올려 두고 필터가 매 요청 확인한다.
 *
 * "이 세션이 유효한가"를 매번 조회하는 게 아니라 **"폐기됐는가"만** 본다 — 평상시에는
 * 목록이 비어 있어 Redis GET 한 번이 전부다. 필터가 이미 매 요청 DB 를 치므로
 * (existsByIdAndDeletedAtIsNull) 이 GET 이 응답 시간을 좌우하지 않는다.
 *
 * TTL 이 access 수명인 이유: 그 시간이 지나면 폐기된 sid 를 실은 access token 이 어차피
 * 만료된다. refresh 쪽은 세션 삭제가 막는다(UserSessionStore 에 없음 → 400). 그래서
 * 목록이 무한히 커질 수 없다 — 항목은 토큰 남은 수명만큼만 산다.
 */
@Component
public class SessionRevocationStore {

    static final String KEY_PREFIX = "revoked-session:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public SessionRevocationStore(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    public void revoke(String sid) {
        redisTemplate.opsForValue()
                .set(KEY_PREFIX + sid, "1", Duration.ofMillis(jwtProperties.accessTokenValidity()));
    }

    public boolean isRevoked(String sid) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + sid));
    }
}
