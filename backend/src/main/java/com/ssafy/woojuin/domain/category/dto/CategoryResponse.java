package com.ssafy.woojuin.domain.category.dto;

import com.ssafy.woojuin.domain.category.entity.Category;

public record CategoryResponse(Long categoryId, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
