package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.item.Item;
import com.ssafy.woojuin.domain.item.ItemType;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.time.Instant;

/**
 * 아이템 상세/목록 카드 공통 응답. aiResult/tags/category는 해당 도메인(AI 처리, 태그)이
 * 아직 없어 포함하지 않는다 — 나중에 그 도메인이 생기면 이 레코드에 필드를 추가한다.
 */
public record ItemResponse(
        Long itemId,
        ItemType type,
        ItemStatus status,
        String title,
        String url,
        String content,
        String s3Key,
        Preview preview,
        boolean favorite,
        Instant createdAt) {

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
                item.getS3Key(),
                new Preview(item.getPreviewThumbnailUrl(), item.getPreviewDescription()),
                item.isFavorite(),
                item.getCreatedAt());
    }
}
