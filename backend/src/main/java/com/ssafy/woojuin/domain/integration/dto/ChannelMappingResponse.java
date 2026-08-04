package com.ssafy.woojuin.domain.integration.dto;

import com.ssafy.woojuin.domain.integration.entity.ChatChannelMapping;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;

public record ChannelMappingResponse(ChatPlatform platform, String channelId, Long workspaceId) {
    public static ChannelMappingResponse from(ChatChannelMapping mapping) {
        return new ChannelMappingResponse(
                mapping.getPlatform(), mapping.getChannelId(), mapping.getWorkspace().getId());
    }
}
