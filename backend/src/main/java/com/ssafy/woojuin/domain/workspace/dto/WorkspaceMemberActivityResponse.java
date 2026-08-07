package com.ssafy.woojuin.domain.workspace.dto;

import com.ssafy.woojuin.domain.auth.entity.AvatarColor;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberActivity;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberActivityType;

import java.time.OffsetDateTime;

public record WorkspaceMemberActivityResponse(Long userId, String nickname, AvatarColor avatarColor,
                                                boolean withdrawn, WorkspaceMemberActivityType type,
                                                OffsetDateTime occurredAt) {

    private static final String WITHDRAWN_USER_NICKNAME = "탈퇴한 사용자";
    private static final AvatarColor WITHDRAWN_USER_AVATAR_COLOR = AvatarColor.WHITE;

    public static WorkspaceMemberActivityResponse of(WorkspaceMemberActivity activity) {
        User user = activity.getUser();
        if (user.isWithdrawn()) {
            return new WorkspaceMemberActivityResponse(user.getId(), WITHDRAWN_USER_NICKNAME,
                    WITHDRAWN_USER_AVATAR_COLOR, true, activity.getType(), activity.getOccurredAt());
        }
        return new WorkspaceMemberActivityResponse(user.getId(), user.getNickname(), user.getAvatarColor(),
                false, activity.getType(), activity.getOccurredAt());
    }
}
