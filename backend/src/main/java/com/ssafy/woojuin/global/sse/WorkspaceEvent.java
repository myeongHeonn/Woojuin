package com.ssafy.woojuin.global.sse;

/**
 * SSE로 클라이언트에 흘려보내는 변경 신호.
 *
 * <p><b>데이터가 아니라 신호만 보낸다.</b> 바뀐 내용을 실어 보내면 서버가 클라이언트의
 * 캐시 구조를 알아야 하고 페이로드도 커진다. 대신 "이 워크스페이스의 무엇이 바뀌었다"만
 * 알리고, 실제 조회는 클라이언트가 평소 쓰던 REST로 다시 하게 둔다
 * (react-query invalidateQueries → 자동 refetch).
 *
 * @param type        무엇이 바뀌었는지 — 클라이언트는 이걸로 무효화할 캐시를 고른다
 * @param workspaceId 어느 워크스페이스인지
 */
public record WorkspaceEvent(WorkspaceEventType type, Long workspaceId) {

    public static WorkspaceEvent of(WorkspaceEventType type, Long workspaceId) {
        return new WorkspaceEvent(type, workspaceId);
    }
}
