package com.ssafy.woojuin.domain.auth.session;

import com.ssafy.woojuin.domain.auth.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionRevocationStoreTest {

    private static final long ACCESS_VALIDITY = 3_600_000L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SessionRevocationStore revocationStore;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        revocationStore = new SessionRevocationStore(
                redisTemplate, new JwtProperties("secret", ACCESS_VALIDITY, 1_209_600_000L));
    }

    @Test
    @DisplayName("폐기 항목의 TTL 은 access 수명 — 그 뒤엔 토큰 자체가 만료라 목록이 무한히 크지 않다")
    void revoke_storesWithAccessTokenTtl() {
        revocationStore.revoke("sid-1");

        verify(valueOperations).set("revoked-session:sid-1", "1", Duration.ofMillis(ACCESS_VALIDITY));
    }

    @Test
    @DisplayName("폐기된 sid 는 true")
    void isRevoked_revokedSid_returnsTrue() {
        when(redisTemplate.hasKey("revoked-session:sid-1")).thenReturn(true);

        assertThat(revocationStore.isRevoked("sid-1")).isTrue();
    }

    @Test
    @DisplayName("폐기되지 않은 sid 는 false")
    void isRevoked_unknownSid_returnsFalse() {
        when(redisTemplate.hasKey("revoked-session:sid-2")).thenReturn(false);

        assertThat(revocationStore.isRevoked("sid-2")).isFalse();
    }
}
