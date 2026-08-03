package com.ssafy.woojuin.domain.item.dto;

import java.util.List;

/**
 * 검색 응답. {@link ItemListResponse}와 같은 필드에 폴백·보충 표시가 더 붙는다 —
 * content 배열이 목록과 동일해서 클라이언트가 아이템 카드 컴포넌트를 그대로 재사용할 수 있다.
 *
 * <p>플래그는 어느 폴백을 탔는지 알린다. 이유를 알리지 않으면 사용자가 엉뚱한 결과로
 * 오해하므로, 클라이언트는 이 값으로 안내 문구를 띄우면 된다. partialMatch와 semanticMatch가
 * 동시에 true가 되는 경우는 없다(의미 검색 폴백은 ANY 폴백까지 0건일 때만 돈다).
 * partialMatch와 보충({@code semanticSupplementCount > 0})은 공존할 수 있다.
 *
 * @param partialMatch  모든 단어를 포함한 결과가 없어 일부만 포함한 결과로 폴백했음
 *                      ("일부만 일치하는 결과입니다").
 * @param semanticMatch 문자가 일치하는 결과가 아예 없어 의미상 비슷한 아이템으로 폴백했음
 *                      ("비슷한 항목을 찾았어요"). 결과는 관련도(거리)순 정렬이다.
 * @param semanticSupplementCount 이 페이지 content의 <b>끝에서부터</b> 이 개수만큼은 키워드
 *                      일치가 아니라 의미 검색으로 보충된 아이템이다("비슷한 항목" 구분선을
 *                      그 앞에 그리면 된다). 키워드 결과가 한 페이지를 못 채울 때만 보충이
 *                      붙는다. totalElements에는 보충 전체 건수가 포함되므로, 키워드 결과가
 *                      끝난 뒤 페이지는 content 전체가 보충일 수 있다.
 */
public record ItemSearchResponse(
        List<ItemSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        boolean partialMatch,
        boolean semanticMatch,
        int semanticSupplementCount) {

    public static ItemSearchResponse from(ItemListResponse list, boolean partialMatch) {
        return new ItemSearchResponse(
                list.content(), list.page(), list.size(), list.totalElements(), partialMatch, false, 0);
    }

    public static ItemSearchResponse fromSemantic(ItemListResponse list) {
        return new ItemSearchResponse(
                list.content(), list.page(), list.size(), list.totalElements(), false, true, 0);
    }

    public static ItemSearchResponse empty(int page, int size) {
        return new ItemSearchResponse(List.of(), page, size, 0, false, false, 0);
    }
}
