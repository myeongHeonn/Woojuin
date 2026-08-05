package com.ssafy.woojuin.domain.integration.dto;

import com.ssafy.woojuin.domain.integration.entity.ChatAccountConnection;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;

import java.time.OffsetDateTime;

/**
 * 내가 연결한 채팅 계정 하나 — 기기·앱 관리 화면의 "연결된 앱" 행.
 *
 * 계정 이름(username)이 없는 이유: ChatAccountConnection 에 사람이 읽을 이름 필드가 없고,
 * externalUserId 는 Discord 스노우플레이크 같은 불투명 id 라 화면에 내보내도 의미가 없다.
 * 플랫폼·연결 시각·기본 워크스페이스만으로 가기로 결정했다(S15P11C105-461) — 표시 이름을
 * 저장하려면 컬럼·마이그레이션이 필요해서, 같은 플랫폼에 계정을 둘 연결하는 경우가 실제로
 * 생기면 그때 붙인다.
 */
public record ChatConnectionResponse(
        Long id,
        ChatPlatform platform,
        OffsetDateTime connectedAt,
        Long defaultWorkspaceId,
        String defaultWorkspaceName) {

    public static ChatConnectionResponse from(ChatAccountConnection connection) {
        Workspace workspace = connection.getDefaultWorkspace();
        return new ChatConnectionResponse(
                connection.getId(),
                connection.getPlatform(),
                connection.getCreatedAt(),
                workspace == null ? null : workspace.getId(),
                workspace == null ? null : workspace.getName());
    }
}
