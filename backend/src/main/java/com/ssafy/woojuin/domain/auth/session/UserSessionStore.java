package com.ssafy.woojuin.domain.auth.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.woojuin.domain.auth.jwt.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 로그인 세션을 기기별로 저장한다 — RefreshTokenStore(사용자당 문자열 하나)의 후임.
 *
 * 한 사용자의 세션들을 해시 하나(`sessions:{userId}`, 필드=sid)에 담는다. 목록 조회와
 * 전체 해제가 키 하나로 끝나고, 사용자 단위 SCAN 이 필요 없다.
 *
 * Redis 해시는 필드별 TTL 이 없어 만료된 세션이 필드로 남는다 — refresh 는 JWT 자체의
 * exp 가 먼저 거부하므로 뚫리지는 않지만, 기기 목록에 유령이 남으므로 **읽는 시점에
 * 걸러 지운다**(lazy prune).
 *
 * 키 TTL 은 저장 시 refresh 수명으로 걸어 둔다 — 마지막 로그인으로부터 refresh 수명이
 * 지나면 키째 사라져 빈 해시가 쌓이지 않는다.
 *
 * ── 옛 키(`refresh-token:{userId}`)는 읽지 않는다 ─────────────────────────────
 * 배포 순간 모든 사용자가 한 번 다시 로그인한다. 폴백을 두지 않기로 결정했다
 * (S15P11C105-459) — 1회 로그아웃은 감수할 수준이고, 폴백은 한 릴리스 뒤 지워야 하는
 * 잊힐 코드가 된다.
 */
@Component
public class UserSessionStore {

    static final String KEY_PREFIX = "sessions:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;

    public UserSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                            JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jwtProperties = jwtProperties;
    }

    public void save(Long userId, UserSession session) {
        String key = KEY_PREFIX + userId;
        redisTemplate.opsForHash().put(key, session.sid(), serialize(session));
        redisTemplate.expire(key, Duration.ofMillis(jwtProperties.refreshTokenValidity()));
    }

    public Optional<UserSession> find(Long userId, String sid) {
        Object raw = redisTemplate.opsForHash().get(KEY_PREFIX + userId, sid);
        if (raw == null) return Optional.empty();

        UserSession session = deserialize(raw.toString());
        if (session.isExpired(jwtProperties.refreshTokenValidity())) {
            delete(userId, sid);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** 살아 있는 세션 전부. 만료된 필드는 이 자리에서 지운다 — 기기 목록에 유령이 남지 않게 */
    public List<UserSession> list(Long userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(KEY_PREFIX + userId);
        List<UserSession> alive = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            UserSession session = deserialize(entry.getValue().toString());
            if (session.isExpired(jwtProperties.refreshTokenValidity())) {
                delete(userId, session.sid());
            } else {
                alive.add(session);
            }
        }
        return alive;
    }

    /** refresh 성공 시 마지막 사용 시각을 갱신한다 — 기기 목록의 "마지막 사용" 이 이 값이다 */
    public void touch(Long userId, String sid) {
        find(userId, sid).ifPresent(session ->
                redisTemplate.opsForHash()
                        .put(KEY_PREFIX + userId, sid, serialize(session.touched())));
    }

    public void delete(Long userId, String sid) {
        redisTemplate.opsForHash().delete(KEY_PREFIX + userId, sid);
    }

    /** 전체 해제·탈퇴용. 지운 sid 들을 돌려준다 — 호출자가 폐기 목록에 올려 즉시 차단한다 */
    public List<String> deleteAll(Long userId) {
        List<String> sids = list(userId).stream().map(UserSession::sid).toList();
        redisTemplate.delete(KEY_PREFIX + userId);
        return sids;
    }

    private String serialize(UserSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("세션 직렬화에 실패했습니다", e);
        }
    }

    private UserSession deserialize(String raw) {
        try {
            return objectMapper.readValue(raw, UserSession.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("세션 역직렬화에 실패했습니다", e);
        }
    }
}
