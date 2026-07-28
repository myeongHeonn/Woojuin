package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.item.entity.Item;

/**
 * 즐겨찾기 등록/해제 응답. 상세 전체를 돌려주지 않는 건 별 아이콘만 갱신하면 되는
 * 호출이라서다 — 클라이언트가 응답의 favorite로 낙관적 갱신을 되맞출 수 있으면 충분하다.
 */
public record ItemFavoriteResponse(Long itemId, boolean favorite) {

    public static ItemFavoriteResponse from(Item item) {
        return new ItemFavoriteResponse(item.getId(), item.isFavorite());
    }
}
