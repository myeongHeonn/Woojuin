package com.ssafy.woojuin.domain.item.dto;

import java.util.List;

/**
 * 검색 응답. {@link ItemListResponse}와 같은 필드에 {@code partialMatch} 하나만 더 붙는다 —
 * content 배열이 목록과 동일해서 클라이언트가 아이템 카드 컴포넌트를 그대로 재사용할 수 있다.
 *
 * @param partialMatch 모든 단어를 포함한 결과가 없어 일부만 포함한 결과로 폴백했음을 뜻한다.
 *                     클라이언트는 "일부만 일치하는 결과입니다" 같은 안내를 띄우면 된다.
 *                     이유를 알리지 않으면 사용자가 엉뚱한 결과로 오해한다.
 */
public record ItemSearchResponse(
        List<ItemSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        boolean partialMatch) {

    public static ItemSearchResponse from(ItemListResponse list, boolean partialMatch) {
        return new ItemSearchResponse(
                list.content(), list.page(), list.size(), list.totalElements(), partialMatch);
    }

    public static ItemSearchResponse empty(int page, int size) {
        return new ItemSearchResponse(List.of(), page, size, 0, false);
    }
}
