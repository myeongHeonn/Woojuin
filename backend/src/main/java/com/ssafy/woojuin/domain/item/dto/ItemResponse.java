package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.item.Item;
import com.ssafy.woojuin.domain.item.ItemType;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.time.OffsetDateTime;

/**
 * 아이템 상세/목록 카드 공통 응답. summary는 AI 요약(본문이 있을 때만 채워짐)이다.
 * 카테고리 목록은 item_categories 조인 조회가 필요해 아직 포함하지 않는다 — 읽기 경로에서
 * 카테고리를 노출하는 건 후속 작업.
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
        boolean favorite,
        OffsetDateTime createdAt,
        OffsetDateTime deletedAt) {

    public record Preview(String thumbnailUrl, String description) {
    }

    public static ItemResponse from(Item item) {
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
                item.isFavorite(),
                item.getCreatedAt(),
                item.getDeletedAt());
    }
}
