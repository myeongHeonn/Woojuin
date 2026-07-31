package com.ssafy.woojuin.global.sse;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 SSE 신호로 옮긴다.
 *
 * <p>도메인 서비스가 SSE 를 직접 알지 않게 하려는 접점이다 — 서비스는 평소처럼
 * ApplicationEvent 만 발행하고, 그걸 SSE 로 흘릴지는 여기서만 정한다
 * (NotificationService 가 같은 이벤트를 FCM 으로 옮기는 것과 대칭).
 */
@Component
public class WorkspaceEventBroadcaster {

    private final WorkspaceSseRegistry sseRegistry;

    public WorkspaceEventBroadcaster(WorkspaceSseRegistry sseRegistry) {
        this.sseRegistry = sseRegistry;
    }

    /**
     * 도메인 서비스가 직접 알리는 변경(아이템 · 카테고리 · 워크스페이스 · 멤버 등).
     *
     * <p>커밋 후에 보낸다 — 트랜잭션 안에서 보내면 롤백된 변경까지 신호가 나가고,
     * 신호를 받은 클라이언트가 아직 커밋 전인 데이터를 조회해 예전 값을 다시 캐시할 수 있다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkspaceChanged(WorkspaceChangedEvent event) {
        sseRegistry.broadcast(event.workspaceId(), WorkspaceEvent.of(event.type(), event.workspaceId()));
    }
}
