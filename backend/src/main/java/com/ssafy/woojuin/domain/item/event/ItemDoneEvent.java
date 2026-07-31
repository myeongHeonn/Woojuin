package com.ssafy.woojuin.domain.item.event;

/**
 * 아이템이 DONE 또는 PARTIAL로 확정된 시점에 발행된다(AGENTS.md 규칙 6 — FAILED만 발행 안 함).
 * 동기 @EventListener로 소비되면 처리 트랜잭션 안에서 알림이 함께 커밋/롤백된다
 * (WorkspaceCreatedEvent와 대칭).
 *
 * <p>알림은 이 이벤트로, 화면 갱신 신호는 WorkspaceChangedEvent로 분리한다. 후자는
 * AFTER_COMMIT에서 전달되므로 클라이언트가 즉시 재조회해도 커밋된 데이터를 읽는다.
 */
public record ItemDoneEvent(Long itemId) {
}
