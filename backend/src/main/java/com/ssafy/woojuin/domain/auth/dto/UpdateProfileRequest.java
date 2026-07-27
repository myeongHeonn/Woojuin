package com.ssafy.woojuin.domain.auth.dto;

import com.ssafy.woojuin.domain.auth.entity.AvatarColor;

public record UpdateProfileRequest(String nickname, String profileImageUrl, AvatarColor avatarColor) {
}
