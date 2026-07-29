package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.item.entity.ItemType;
import java.util.List;

/**
 * 지도 뷰 핀 하나 (FR-032). {@code GET /api/workspaces/{workspaceId}/items/geo} 응답 원소.
 *
 * <p>{@link ItemSummaryResponse}를 재사용하지 않는 이유가 둘이다. 카테고리 형태가 다르고
 * (평면 {@code categoryIds} 배열), 목록 응답이 끌고 오는 {@code summary}·{@code preview}·
 * {@code imageUrl}(IMAGE마다 S3 presign 발생)이 지도엔 전부 불필요하다.
 *
 * <p>카테고리를 {@code {categoryId, name, color}} 객체가 아니라 id 배열로 내리는 이유:
 * 프론트는 핀 색과 필터 칩을 그리려고 카테고리 목록 API를 이미 받아둔 상태이므로 id만으로
 * 조립할 수 있고, 핀마다 이름·색을 중복 전송하면 페이로드만 커진다.
 */
public record ItemGeoResponse(
        Long itemId,
        ItemType type,
        String title,
        List<Long> categoryIds,
        Double lat,
        Double lng,
        String address) {
}
