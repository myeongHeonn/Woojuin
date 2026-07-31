package com.ssafy.woojuin.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 서블릿 없는 단위 테스트라 emitter.complete()가 onCompletion 콜백을 태우지 않는다.
 * 대신 complete 이후의 send가 IllegalStateException을 던지는 성질을 이용해
 * "전송 실패한 연결을 정리한다"는 실제 경로(broadcast · heartbeat)를 검증한다.
 */
class WorkspaceSseRegistryTest {

    private WorkspaceSseRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WorkspaceSseRegistry();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<Long, SseEmitter>> internalMap() {
        return (Map<Long, Map<Long, SseEmitter>>) ReflectionTestUtils.getField(registry, "emittersByWorkspace");
    }

    @Test
    void 구독하면_워크스페이스에_연결이_등록된다() {
        registry.subscribe(1L);
        registry.subscribe(1L);

        assertThat(internalMap().get(1L)).hasSize(2);
    }

    @Test
    void 브로드캐스트가_끊긴_연결을_정리하고_빈_맵은_남기지_않는다() {
        SseEmitter dead = registry.subscribe(1L);
        dead.complete();   // 이후 send는 IllegalStateException — 끊긴 연결과 같은 신호

        registry.broadcast(1L, WorkspaceEvent.of(WorkspaceEventType.ITEM, 1L));

        // 마지막 연결이 빠지면 workspaceId 항목 자체가 없어야 한다 — 빈 맵이 남으면
        // 워크스페이스 수만큼 유령 항목이 쌓인다.
        assertThat(internalMap()).doesNotContainKey(1L);
    }

    @Test
    void 한_연결의_전송_실패가_다른_연결의_수신을_막지_않는다() {
        SseEmitter dead = registry.subscribe(1L);
        registry.subscribe(1L);
        dead.complete();

        assertThatCode(() -> registry.broadcast(1L, WorkspaceEvent.of(WorkspaceEventType.ITEM, 1L)))
                .doesNotThrowAnyException();

        assertThat(internalMap().get(1L)).hasSize(1);
    }

    @Test
    void 구독자가_없는_워크스페이스로의_브로드캐스트는_아무_일도_없다() {
        assertThatCode(() -> registry.broadcast(99L, WorkspaceEvent.of(WorkspaceEventType.ITEM, 99L)))
                .doesNotThrowAnyException();
    }

    @Test
    void 하트비트는_끊긴_연결만_정리하고_살아있는_연결은_남긴다() {
        SseEmitter dead = registry.subscribe(1L);
        registry.subscribe(1L);
        dead.complete();

        registry.sendHeartbeats();

        assertThat(internalMap().get(1L)).hasSize(1);
    }
}
