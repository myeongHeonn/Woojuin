package com.ssafy.woojuin.domain.auth.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.woojuin.domain.auth.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionStoreTest {

    private static final long REFRESH_VALIDITY = 1_209_600_000L; // 14일

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserSessionStore sessionStore;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.<Object, Object>opsForHash()).thenReturn(hashOperations);
        sessionStore = new UserSessionStore(
                redisTemplate, objectMapper, new JwtProperties("secret", 3_600_000L, REFRESH_VALIDITY));
    }

    @Test
    @DisplayName("저장하면 sid 필드로 들어가고 키 TTL 이 refresh 수명으로 걸린다")
    void save_putsFieldAndSetsKeyTtl() throws Exception {
        UserSession session = UserSession.start("sid-1", "refresh-token", "Mozilla/5.0");

        sessionStore.save(1L, session);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(hashOperations).put(eq("sessions:1"), eq("sid-1"), json.capture());
        assertThat(objectMapper.readValue(json.getValue(), UserSession.class)).isEqualTo(session);
        // 마지막 로그인으로부터 refresh 수명이 지나면 키째 사라진다 — 빈 해시가 안 쌓인다
        verify(redisTemplate).expire("sessions:1", Duration.ofMillis(REFRESH_VALIDITY));
    }

    @Test
    @DisplayName("살아 있는 세션은 찾아진다")
    void find_aliveSession_returnsIt() throws Exception {
        UserSession session = UserSession.start("sid-1", "refresh-token", "Mozilla/5.0");
        when(hashOperations.get("sessions:1", "sid-1"))
                .thenReturn(objectMapper.writeValueAsString(session));

        Optional<UserSession> found = sessionStore.find(1L, "sid-1");

        assertThat(found).contains(session);
    }

    @Test
    @DisplayName("만료된 세션은 읽는 자리에서 지우고 없다고 답한다 — 해시엔 필드 TTL 이 없다")
    void find_expiredSession_prunesAndReturnsEmpty() throws Exception {
        long expiredCreatedAt = System.currentTimeMillis() - REFRESH_VALIDITY - 1_000;
        UserSession expired = new UserSession(
                "sid-old", "refresh-token", "Mozilla/5.0", expiredCreatedAt, expiredCreatedAt);
        when(hashOperations.get("sessions:1", "sid-old"))
                .thenReturn(objectMapper.writeValueAsString(expired));

        Optional<UserSession> found = sessionStore.find(1L, "sid-old");

        assertThat(found).isEmpty();
        verify(hashOperations).delete("sessions:1", "sid-old");
    }

    @Test
    @DisplayName("목록은 산 것만 돌려주고 만료된 필드는 그 자리에서 지운다 — 기기 목록에 유령이 없다")
    void list_returnsAliveOnly_prunesExpired() throws Exception {
        UserSession alive = UserSession.start("sid-1", "rt-1", "Mozilla/5.0");
        long expiredCreatedAt = System.currentTimeMillis() - REFRESH_VALIDITY - 1_000;
        UserSession expired = new UserSession(
                "sid-old", "rt-old", "Mozilla/5.0", expiredCreatedAt, expiredCreatedAt);
        when(hashOperations.entries("sessions:1")).thenReturn(Map.of(
                "sid-1", objectMapper.writeValueAsString(alive),
                "sid-old", objectMapper.writeValueAsString(expired)));

        List<UserSession> sessions = sessionStore.list(1L);

        assertThat(sessions).containsExactly(alive);
        verify(hashOperations).delete("sessions:1", "sid-old");
    }

    @Test
    @DisplayName("touch 는 lastUsedAt 만 앞으로 간다 — 기기 목록의 '마지막 사용'이 이 값이다")
    void touch_updatesLastUsedAt() throws Exception {
        long createdAt = System.currentTimeMillis() - 10_000;
        UserSession session = new UserSession("sid-1", "refresh-token", "Mozilla/5.0", createdAt, createdAt);
        when(hashOperations.get("sessions:1", "sid-1"))
                .thenReturn(objectMapper.writeValueAsString(session));

        sessionStore.touch(1L, "sid-1");

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(hashOperations).put(eq("sessions:1"), eq("sid-1"), json.capture());
        UserSession touched = objectMapper.readValue(json.getValue(), UserSession.class);
        assertThat(touched.lastUsedAt()).isGreaterThan(createdAt);
        assertThat(touched.createdAt()).isEqualTo(createdAt);
        assertThat(touched.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("전체 삭제는 지운 sid 들을 돌려준다 — 호출자가 폐기 목록에 올려 즉시 차단한다")
    void deleteAll_returnsRemovedSids() throws Exception {
        UserSession first = UserSession.start("sid-1", "rt-1", "Mozilla/5.0");
        UserSession second = UserSession.start("sid-2", "rt-2", "Mozilla/5.0");
        when(hashOperations.entries("sessions:1")).thenReturn(Map.of(
                "sid-1", objectMapper.writeValueAsString(first),
                "sid-2", objectMapper.writeValueAsString(second)));

        List<String> removed = sessionStore.deleteAll(1L);

        assertThat(removed).containsExactlyInAnyOrder("sid-1", "sid-2");
        verify(redisTemplate).delete("sessions:1");
    }
}
