package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.item.entity.Item;

/**
 * URL 아이템 미리보기(트랙 A) 결과. thumbnailUrl은 oEmbed/OpenGraph에서 얻은 대표
 * 이미지, description은 페이지 메타데이터(og:description 등)다 — AI 요약(summary)과 달리
 * 본문/AI 없이도 채워지므로 요약이 없는 아이템의 설명 폴백으로 쓸 수 있다.
 * 목록·상세 응답이 공유하되, <b>목록에서는</b> thumbnailUrl이 S3에 캐시한 저용량 썸네일의
 * presigned URL로 대체될 수 있다({@code ItemSummaryAssembler.previewOf}) — 외부 og:image
 * 원본은 크고 느려서 카드에는 부적합하다. 상세는 항상 외부 원본을 그대로 쓴다.
 */
public record ItemPreview(String thumbnailUrl, String description) {

    public static ItemPreview from(Item item) {
        return new ItemPreview(item.getPreviewThumbnailUrl(), item.getPreviewDescription());
    }
}
