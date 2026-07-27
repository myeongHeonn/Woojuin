package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.category.dto.CategoryResponse;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 아이템 상세/목록 카드 공통 응답. summary는 AI 요약(본문이 있을 때만 채워짐), categories는
 * 그 아이템에 연결된 카테고리들(AI 분류 + 사용자 지정, 다대다)이다. imageUrl은 IMAGE
 * 아이템 원본을 브라우저가 바로 읽을 수 있는 presigned URL로, IMAGE가 아니면 null이다.
 */
public record ItemResponse(
        Long itemId,
        ItemType type,
        ItemStatus status,
        String title,
        String url,
        String content,
        String summary,
        Preview preview,
        String imageUrl,
        List<CategoryResponse> categories,
        boolean favorite,
        OffsetDateTime createdAt,
        OffsetDateTime deletedAt) {

    public record Preview(String thumbnailUrl, String description) {
    }

    /** 카테고리·이미지 URL을 채우지 않는 응답(카테고리/이미지가 불필요한 경로용). */
    public static ItemResponse from(Item item) {
        return from(item, List.of(), null);
    }

    public static ItemResponse from(Item item, List<CategoryResponse> categories, String imageUrl) {
        return new ItemResponse(
                item.getId(),
                item.getType(),
                item.getStatus(),
                item.getTitle(),
                item.getUrl(),
                item.getContent(),
                item.getSummary(),
                new Preview(item.getPreviewThumbnailUrl(), item.getPreviewDescription()),
                imageUrl,
                categories,
                item.isFavorite(),
                item.getCreatedAt(),
                item.getDeletedAt());
    }
}
