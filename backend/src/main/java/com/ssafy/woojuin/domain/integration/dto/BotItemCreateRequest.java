package com.ssafy.woojuin.domain.integration.dto;

import com.ssafy.woojuin.domain.item.entity.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BotItemCreateRequest(
        @NotBlank @Size(max = 100) String channelId,
        @NotNull ItemType type,
        String url,
        String content) {
}
