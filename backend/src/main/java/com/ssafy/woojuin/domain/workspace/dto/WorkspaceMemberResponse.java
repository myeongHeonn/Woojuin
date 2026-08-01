package com.ssafy.woojuin.domain.workspace.dto;

import com.ssafy.woojuin.domain.auth.entity.AvatarColor;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceRole;

import java.time.OffsetDateTime;

public record WorkspaceMemberResponse(Long userId, String nickname, String email, WorkspaceRole role,
                                       OffsetDateTime joinedAt, boolean withdrawn,
                                       AvatarColor avatarColor) {

    private static final String WITHDRAWN_USER_NICKNAME = "탈퇴한 사용자";

    /** 탈퇴한 멤버는 개인을 식별할 정보를 지우므로 색상도 내리지 않고 중립색으로 통일한다. */
    private static final AvatarColor WITHDRAWN_USER_AVATAR_COLOR = AvatarColor.WHITE;

    public static WorkspaceMemberResponse of(WorkspaceMember member) {
        User user = member.getUser();
        if (user.isWithdrawn()) {
            return new WorkspaceMemberResponse(user.getId(), WITHDRAWN_USER_NICKNAME, null,
                    member.getRole(), member.getJoinedAt(), true, WITHDRAWN_USER_AVATAR_COLOR);
        }
        return new WorkspaceMemberResponse(user.getId(), user.getNickname(), user.getEmail(),
                member.getRole(), member.getJoinedAt(), false, user.getAvatarColor());
    }
}
