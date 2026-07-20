package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.item.Item;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.time.Instant;

public record ItemCreateResponse(Long itemId, ItemStatus status, Instant createdAt) {

    public static ItemCreateResponse from(Item item) {
        return new ItemCreateResponse(item.getId(), item.getStatus(), item.getCreatedAt());
    }
}
