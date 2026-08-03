package com.ssafy.woojuin.domain.integration.dto;

import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChannelMappingRequest(
        @NotNull ChatPlatform platform,
        @NotBlank @Size(max = 100) String channelId,
        @NotNull Long workspaceId) {
}
