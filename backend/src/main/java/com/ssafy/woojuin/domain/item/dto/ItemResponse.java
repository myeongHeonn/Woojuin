package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.category.dto.CategoryResponse;
import com.ssafy.woojuin.domain.item.Item;
import com.ssafy.woojuin.domain.item.ItemType;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 아이템 상세/목록 카드 공통 응답. summary는 AI 요약(본문이 있을 때만 채워짐), categories는
 * 그 아이템에 연결된 카테고리들(AI 분류 + 사용자 지정, 다대다)이다.
 */
public record ItemResponse(
        Long itemId,
        ItemType type,
        ItemStatus status,
        String title,
        String url,
        String content,
        String summary,
        String s3Key,
        Preview preview,
        List<CategoryResponse> categories,
        boolean favorite,
        OffsetDateTime createdAt,
        OffsetDateTime deletedAt) {

    public record Preview(String thumbnailUrl, String description) {
    }

    /** 카테고리를 채우지 않는 응답(상태 조회 등 카테고리가 불필요한 경로용). */
    public static ItemResponse from(Item item) {
        return from(item, List.of());
    }

    public static ItemResponse from(Item item, List<CategoryResponse> categories) {
        return new ItemResponse(
                item.getId(),
                item.getType(),
                item.getStatus(),
                item.getTitle(),
                item.getUrl(),
                item.getContent(),
                item.getSummary(),
                item.getS3Key(),
                new Preview(item.getPreviewThumbnailUrl(), item.getPreviewDescription()),
                categories,
                item.isFavorite(),
                item.getCreatedAt(),
                item.getDeletedAt());
    }
}
