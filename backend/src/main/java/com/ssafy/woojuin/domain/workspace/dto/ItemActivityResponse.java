package com.ssafy.woojuin.domain.workspace.dto;

/** {@code GET /api/workspaces/{workspaceId}/item-activity} 응답. */
public record ItemActivityResponse(boolean hasNewActivity) {
}
