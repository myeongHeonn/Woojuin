package com.ssafy.woojuin.domain.item.event;

/**
 * 아이템이 DONE으로 확정된 시점에만 발행된다(AGENTS.md 규칙 6 — FAILED/PARTIAL은 발행 안 함).
 * 동기 @EventListener로 소비되면 처리 트랜잭션 안에서 알림이 함께 커밋/롤백된다
 * (WorkspaceCreatedEvent와 대칭).
 */
public record ItemDoneEvent(Long itemId) {
}
