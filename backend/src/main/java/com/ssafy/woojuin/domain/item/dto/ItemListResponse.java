package com.ssafy.woojuin.domain.item.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ItemListResponse(List<ItemResponse> content, int page, int size, long totalElements) {

    public static ItemListResponse from(Page<ItemResponse> page) {
        return new ItemListResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
