package com.ssafy.woojuin.domain.auth.dto;

import com.ssafy.woojuin.domain.auth.entity.AuthProvider;
import com.ssafy.woojuin.domain.auth.entity.User;

public record UserProfileResponse(Long id, String email, String nickname, String profileImageUrl,
                                   AuthProvider provider, boolean emailVerified, Long personalSpaceId) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getNickname(),
                user.getProfileImageUrl(), user.getProvider(), user.isEmailVerified(),
                user.getPersonalWorkspaceId());
    }
}
