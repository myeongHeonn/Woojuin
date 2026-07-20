package com.ssafy.woojuin.domain.auth.dto;

import com.ssafy.woojuin.domain.auth.entity.AuthProvider;

public record UserProfileResponse(Long id, String email, String nickname, String profileImageUrl,
                                   AuthProvider provider, boolean emailVerified) {
}
