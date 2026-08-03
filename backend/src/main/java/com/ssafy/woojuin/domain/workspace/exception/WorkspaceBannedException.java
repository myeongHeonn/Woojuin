package com.ssafy.woojuin.domain.workspace.exception;

/** 이 워크스페이스에서 강제 추방된 이력이 있는 유저가 초대 링크로 재입장을 시도할 때 던진다. */
public class WorkspaceBannedException extends RuntimeException {

    public WorkspaceBannedException(Long workspaceId) {
        super("COMMON_403: 이 워크스페이스에서 추방된 이력이 있어 재입장할 수 없습니다 (workspaceId=" + workspaceId + ")");
    }
}
