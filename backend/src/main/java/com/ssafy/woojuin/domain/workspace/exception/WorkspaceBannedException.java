package com.ssafy.woojuin.domain.workspace.exception;

/**
 * 이 워크스페이스에서 강제 추방된 이력이 있는 유저가 초대 링크로 재입장을 시도하거나,
 * 추방된 사실을 모른 채 워크스페이스 화면에 다시 들어오려 할 때 던진다.
 * 메시지에 "추방"이 포함되어 있어야 한다 — 프론트가 이 문자열로 자진 탈퇴/비회원과
 * 구분해 전용 안내를 보여준다(InvitePage, useWorkspaceEvictionGuard).
 */
public class WorkspaceBannedException extends RuntimeException {

    public WorkspaceBannedException(Long workspaceId) {
        super("COMMON_403: 이 워크스페이스에서 추방된 이력이 있어 재입장할 수 없습니다 (workspaceId=" + workspaceId + ")");
    }
}
