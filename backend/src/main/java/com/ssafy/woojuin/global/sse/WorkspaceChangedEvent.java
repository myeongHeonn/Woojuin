package com.ssafy.woojuin.global.sse;

/**
 * 워크스페이스 안에서 무언가 바뀌었을 때 도메인 서비스가 발행하는 이벤트.
 *
 * <p>도메인이 SSE 를 직접 알지 않게 하는 완충재다 — 서비스는 "바뀌었다"만 알리고,
 * 그걸 SSE 로 흘릴지는 WorkspaceEventBroadcaster 가 정한다.
 *
 * <p>ItemDoneEvent 처럼 종류마다 record 를 따로 두지 않은 이유: 이 이벤트들은 처리 로직이
 * 전부 "해당 워크스페이스에 신호 보내기"로 같아서, 종류를 필드로 받으면 리스너가 하나로 끝난다.
 */
public record WorkspaceChangedEvent(Long workspaceId, WorkspaceEventType type, WorkspaceMemberAction memberAction) {

    public static WorkspaceChangedEvent of(Long workspaceId, WorkspaceEventType type) {
        return new WorkspaceChangedEvent(workspaceId, type, null);
    }

    /** MEMBER 신호에 가입/탈퇴/추방 구분을 함께 싣는다. */
    public static WorkspaceChangedEvent ofMemberAction(Long workspaceId, WorkspaceMemberAction memberAction) {
        return new WorkspaceChangedEvent(workspaceId, WorkspaceEventType.MEMBER, memberAction);
    }
}
