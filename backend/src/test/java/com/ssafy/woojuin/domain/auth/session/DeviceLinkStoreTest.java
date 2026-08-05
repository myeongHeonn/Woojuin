package com.ssafy.woojuin.domain.auth.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceLinkStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DeviceLinkStore linkStore;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        linkStore = new DeviceLinkStore(redisTemplate);
    }

    @Test
    @DisplayName("코드는 6자, 혼동 글자(I·O·0·1) 없이 발급된다 — 워치 화면을 보고 옮겨 적는 값이다")
    void create_producesReadableCode() {
        when(valueOperations.setIfAbsent(startsWith("device-link:"), eq(""), eq(DeviceLinkStore.PENDING_TTL)))
                .thenReturn(true);

        String code = linkStore.create();

        assertThat(code).hasSize(6).matches("[A-HJ-NP-Z2-9]+");
    }

    @Test
    @DisplayName("승인은 계정을 기록하고 TTL 을 짧게 조인다 — 승인된 코드가 오래 살면 안 된다")
    void approve_bindsUserWithShortTtl() {
        when(valueOperations.get("device-link:AB23CD")).thenReturn("");

        boolean approved = linkStore.approve("AB23CD", 7L);

        assertThat(approved).isTrue();
        verify(valueOperations).set(eq("device-link:AB23CD"), eq("7"), eq(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("만료·미발급 코드의 승인은 false")
    void approve_expiredCode_returnsFalse() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThat(linkStore.approve("XXXXXX", 7L)).isFalse();
    }

    @Test
    @DisplayName("소문자로 쳐도 승인된다 — 워치는 대문자로 보여주지만 사람은 소문자로 칠 수 있다")
    void approve_lowercaseInput_normalized() {
        when(valueOperations.get("device-link:AB23CD")).thenReturn("");

        assertThat(linkStore.approve("ab23cd", 7L)).isTrue();
    }

    @Test
    @DisplayName("find 는 세 상태를 구분한다 — 없음·미승인(PENDING)·승인(userId)")
    void find_distinguishesThreeStates() {
        when(valueOperations.get("device-link:GONE99")).thenReturn(null);
        when(valueOperations.get("device-link:WAIT22")).thenReturn("");
        when(valueOperations.get("device-link:DONE33")).thenReturn("7");

        assertThat(linkStore.find("GONE99")).isEmpty();

        Optional<DeviceLinkStore.LinkState> pending = linkStore.find("WAIT22");
        assertThat(pending).isPresent();
        assertThat(pending.get().approved()).isFalse();

        Optional<DeviceLinkStore.LinkState> approved = linkStore.find("DONE33");
        assertThat(approved).isPresent();
        assertThat(approved.get().approved()).isTrue();
        assertThat(approved.get().userId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("consume 은 코드를 지운다 — 토큰과 함께 한 번만 쓰인다")
    void consume_deletesCode() {
        linkStore.consume("AB23CD");

        verify(redisTemplate).delete("device-link:AB23CD");
    }
}
