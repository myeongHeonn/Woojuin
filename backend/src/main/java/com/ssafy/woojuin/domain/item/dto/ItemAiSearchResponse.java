package com.ssafy.woojuin.domain.item.dto;

import java.util.List;

/**
 * AI 모드 검색 응답. content 배열은 목록·검색과 동일해 클라이언트가 아이템 카드 컴포넌트를
 * 그대로 재사용할 수 있고, "AI가 무엇으로 이해했는지"를 알리는 필드가 더 붙는다.
 *
 * @param interpretedQuery AI가 질문에서 뽑아낸 실제 검색어. 결과가 예상과 다를 때 사용자가
 *                         원인을 알 수 있어야 하므로 반드시 노출할 것
 *                         (예: "'파스타집 을지로'(으)로 찾았어요").
 * @param aiPlanned        LLM이 해석했으면 true. API 키가 없거나 LLM 호출이 실패해 규칙 기반으로
 *                         폴백하면 false — 이때는 오타 교정·관련어 확장이 적용되지 않았다는 뜻이다.
 * @param partialMatch     모든 키워드를 포함한 결과가 없어 일부만 포함한 결과로 폴백했음
 *                         (키워드 검색과 동일한 의미).
 */
public record ItemAiSearchResponse(
        List<ItemSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        String interpretedQuery,
        boolean aiPlanned,
        boolean partialMatch) {

    public static ItemAiSearchResponse from(ItemSearchResponse search, String interpretedQuery,
            boolean aiPlanned) {
        return new ItemAiSearchResponse(
                search.content(), search.page(), search.size(), search.totalElements(),
                interpretedQuery, aiPlanned, search.partialMatch());
    }

    public static ItemAiSearchResponse empty(int page, int size, String interpretedQuery,
            boolean aiPlanned) {
        return new ItemAiSearchResponse(List.of(), page, size, 0, interpretedQuery, aiPlanned, false);
    }
}
