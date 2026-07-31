package com.ssafy.woojuin.global.sse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 워크스페이스별 SSE 연결 보관소.
 *
 * <p>같은 워크스페이스를 보는 모든 클라이언트(여러 사람 · 한 사람의 여러 탭)에게 변경을
 * 흘려보내야 하므로 workspaceId 하나에 여러 emitter가 매달린다. 연결이 끊기는 경로가
 * 완료 · 타임아웃 · 오류 셋이라 각각에서 같은 정리 코드를 걸어 맵에 유령이 남지 않게 한다.
 *
 * <p>단일 인스턴스 메모리 보관이다 — 서버를 여러 대로 늘리면 A서버에 붙은 클라이언트가
 * B서버에서 발행한 이벤트를 못 받는다. 그때는 Redis Pub/Sub 릴레이가 필요하다.
 */
@Component
public class WorkspaceSseRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSseRegistry.class);

    /** 이 시간 동안 이벤트가 없으면 서버가 연결을 닫는다. 클라이언트는 자동으로 다시 붙는다. */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    /**
     * 유휴 연결 유지용 하트비트 주기. 호스트 nginx의 proxy_read_timeout 기본값(60초)보다
     * 짧아야 한다 — 이벤트가 없는 60초가 지나면 nginx가 연결을 끊어서, 위 TIMEOUT_MS가
     * 무색하게 클라이언트가 1분마다 재연결을 반복하게 된다.
     */
    private static final long HEARTBEAT_INTERVAL_MS = 25 * 1000L;

    /** workspaceId → (연결 식별자 → emitter). 바깥·안쪽 모두 동시 접근이라 concurrent 로 둔다. */
    private final Map<Long, Map<Long, SseEmitter>> emittersByWorkspace = new ConcurrentHashMap<>();

    /** 같은 워크스페이스 안에서 연결을 구분하는 일련번호 — 해제할 때 자기 것만 지우기 위함 */
    private final AtomicLong connectionSequence = new AtomicLong();

    /**
     * 새 연결을 등록한다. 프록시(Nginx)가 첫 바이트를 기다리다 끊는 걸 막으려고
     * 연결 직후 확인용 이벤트를 한 번 보낸다.
     */
    public SseEmitter subscribe(Long workspaceId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        long connectionId = connectionSequence.incrementAndGet();

        // remove()와 같은 compute 락 안에서 넣는다. computeIfAbsent로 받은 맵에 바깥에서
        // put 하면, 그 틈에 remove가 "빈 맵"이라며 바깥 맵에서 지운 고아 맵에 들어갈 수
        // 있다 — 그 연결은 브로드캐스트를 영영 못 받는다(StrictMode의 이중 구독이 이 패턴).
        emittersByWorkspace.compute(workspaceId, (key, emitters) -> {
            Map<Long, SseEmitter> target = (emitters != null) ? emitters : new ConcurrentHashMap<>();
            target.put(connectionId, emitter);
            return target;
        });

        // 끊기는 경로가 셋이라 전부 같은 정리를 건다. 하나라도 빠지면 죽은 emitter 가 쌓인다.
        emitter.onCompletion(() -> remove(workspaceId, connectionId));
        emitter.onTimeout(() -> remove(workspaceId, connectionId));
        emitter.onError(throwable -> remove(workspaceId, connectionId));

        try {
            emitter.send(SseEmitter.event().name("connected").data(workspaceId));
        } catch (IOException e) {
            // 연결이 이미 끊긴 경우 — 등록만 해제하고 조용히 넘어간다(호출자가 할 일이 없다)
            remove(workspaceId, connectionId);
        }

        return emitter;
    }

    /**
     * 워크스페이스를 구독 중인 모든 연결에 이벤트를 보낸다.
     *
     * <p>보내다 실패한 연결은 이미 끊긴 것이므로 즉시 정리한다. 한 연결의 실패가 다른
     * 연결 전송을 막지 않도록 예외를 여기서 삼킨다.
     */
    public void broadcast(Long workspaceId, WorkspaceEvent event) {
        Map<Long, SseEmitter> emitters = emittersByWorkspace.get(workspaceId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        emitters.forEach((connectionId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(event.type().eventName()).data(event));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE 전송 실패 — 연결 정리: workspaceId={}, type={}", workspaceId, event.type());
                remove(workspaceId, connectionId);
            }
        });
    }

    /**
     * 모든 연결에 주기적으로 comment 라인을 흘린다.
     *
     * <p>SSE 스펙상 {@code :}로 시작하는 comment는 클라이언트가 무시하므로 프론트 처리가
     * 필요 없다. 목적은 둘 — 프록시(nginx)의 유휴 타임아웃을 갱신해 연결을 유지하고,
     * 브라우저가 조용히 사라진 죽은 연결을 다음 하트비트에서 발견해 정리한다.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeats() {
        emittersByWorkspace.forEach((workspaceId, emitters) ->
                emitters.forEach((connectionId, emitter) -> {
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                    } catch (IOException | IllegalStateException e) {
                        remove(workspaceId, connectionId);
                    }
                }));
    }

    private void remove(Long workspaceId, Long connectionId) {
        // 마지막 연결이 빠지면 맵도 함께 지운다(워크스페이스 수만큼 빈 맵이 쌓이지 않게).
        // subscribe와 같은 compute 락을 타므로 "비어서 지운다"와 "새 연결을 넣는다"가
        // 겹치지 않는다.
        emittersByWorkspace.compute(workspaceId, (key, emitters) -> {
            if (emitters == null) {
                return null;
            }
            emitters.remove(connectionId);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
