package com.ssafy.woojuin.domain.item.repository;

import com.ssafy.woojuin.domain.item.entity.ItemType;

/**
 * {@link ItemRepository#findGeoRows} 프로젝션 — 지도 응답 조립 전 단계의 원시 행.
 *
 * <p>엔티티가 아니라 별도 레코드로 받는 이유는 {@code ItemRepository#findGeoRows} javadoc 참고.
 */
public record ItemGeoRow(
        Long itemId,
        ItemType type,
        String title,
        Double lat,
        Double lng,
        String address) {
}
