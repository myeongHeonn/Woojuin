package com.ssafy.woojuin.domain.workspace.dto;

import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceRole;

import java.time.OffsetDateTime;

public record WorkspaceMemberResponse(Long userId, String nickname, String email, WorkspaceRole role,
                                       OffsetDateTime joinedAt, boolean withdrawn) {

    private static final String WITHDRAWN_USER_NICKNAME = "탈퇴한 사용자";

    public static WorkspaceMemberResponse of(WorkspaceMember member) {
        User user = member.getUser();
        if (user.isWithdrawn()) {
            return new WorkspaceMemberResponse(user.getId(), WITHDRAWN_USER_NICKNAME, null,
                    member.getRole(), member.getJoinedAt(), true);
        }
        return new WorkspaceMemberResponse(user.getId(), user.getNickname(), user.getEmail(),
                member.getRole(), member.getJoinedAt(), false);
    }
}
