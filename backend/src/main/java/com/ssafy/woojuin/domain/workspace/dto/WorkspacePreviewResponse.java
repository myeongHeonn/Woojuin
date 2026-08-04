package com.ssafy.woojuin.domain.workspace.dto;

/**
 * 멤버가 아니어도 볼 수 있는 워크스페이스 최소 정보 — 초대 미리보기(WorkspaceInvitationResponse)와
 * 같은 신뢰 수준이다. 추방/권한없음 모달에 워크스페이스 이름을 띄우는 용도로 쓴다.
 */
public record WorkspacePreviewResponse(Long id, String name) {
}
