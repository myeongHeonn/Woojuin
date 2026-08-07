package com.ssafy.woojuin.domain.integration.dto;

import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;

public record ChatCommand(
        ChatPlatform platform,
        String externalUserId,
        String requestId,
        String text) {
}
