package com.ssafy.woojuin.domain.auth.session;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

/**
 * 워치 링크 코드 저장소 — 브라우저 없는 기기의 로그인(S15P11C105-458).
 *
 * 흐름: 워치가 start 로 코드를 받아 화면에 띄운다 → 사용자가 웹 기기 관리에서 그 코드를
 * 입력(approve, 인증됨) → 워치가 poll 로 승인 여부를 물어 토큰을 받는다.
 * Device Authorization Grant(RFC 8628)의 단순화 — 워치 쪽 입력이 0회가 되는 게 핵심이다.
 *
 * 값의 세 상태를 한 키로 표현한다:
 *   키 없음        → 만료·미발급 (워치는 새 코드를 받아야 한다)
 *   값 = ""       → 발급됐지만 아직 미승인 (poll 은 PENDING)
 *   값 = userId   → 승인됨 (poll 이 소비하고 세션을 만든다)
 *
 * 코드가 인증 수단이 되는 구간은 승인 뒤 소비 전까지뿐이라, 승인 시 TTL 을 짧게(1분)
 * 조인다 — 워치는 수 초 간격으로 폴링하므로 충분하고, 잊힌 승인 코드가 오래 살지 않는다.
 */
@Component
public class DeviceLinkStore {

    static final String KEY_PREFIX = "device-link:";

    /** 혼동되는 글자(I·O·0·1)를 뺀 코드 문자셋 — 워치 화면을 보고 옮겨 적는 값이다 */
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;

    /** 발급 후 승인까지 기다려 주는 시간. 워치 화면에도 같은 값이 안내된다 */
    public static final Duration PENDING_TTL = Duration.ofMinutes(5);
    private static final Duration APPROVED_TTL = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public DeviceLinkStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 새 링크 코드 발급. 충돌하면 다시 뽑는다 — 32^6 공간이라 사실상 첫 시도에 끝난다 */
    public String create() {
        while (true) {
            String code = randomCode();
            Boolean created = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + code, "", PENDING_TTL);
            if (Boolean.TRUE.equals(created)) return code;
        }
    }

    /** 코드에 계정을 바인딩한다. 만료됐거나 없는 코드면 false */
    public boolean approve(String code, Long userId) {
        String key = KEY_PREFIX + normalize(code);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return false;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), APPROVED_TTL);
        return true;
    }

    /** 코드 상태 조회. 키 없음 → empty, 미승인 → PENDING, 승인 → APPROVED(userId) */
    public Optional<LinkState> find(String code) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + normalize(code));
        if (value == null) return Optional.empty();
        if (value.isEmpty()) return Optional.of(LinkState.pending());
        return Optional.of(LinkState.approved(Long.valueOf(value)));
    }

    /** 승인된 코드를 소비한다 — 토큰 발급과 함께 한 번만 쓰이고 사라져야 한다 */
    public void consume(String code) {
        redisTemplate.delete(KEY_PREFIX + normalize(code));
    }

    /** 워치는 대문자로 보여주지만 사람은 소문자로 칠 수 있다 — 서버가 관용을 가진다 */
    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]);
        }
        return sb.toString();
    }

    public record LinkState(boolean approved, Long userId) {
        static LinkState pending() {
            return new LinkState(false, null);
        }

        static LinkState approved(Long userId) {
            return new LinkState(true, userId);
        }
    }
}
