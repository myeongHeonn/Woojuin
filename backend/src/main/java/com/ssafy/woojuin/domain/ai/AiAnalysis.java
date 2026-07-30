package com.ssafy.woojuin.domain.ai;

import java.util.List;

/**
 * AI 분석 결과.
 *
 * <p>title은 AI가 다듬은 제목이다. ai-mix 프롬프트가 "기존 제목이 이미 구체적이면 유지"하도록
 * 설계돼 있어 파일명 제목(이미지)·제목 없는 메모에서 효과가 크다. null이면 기존 제목을 유지한다.
 *
 * <p>categories는 카테고리 <b>이름</b> 목록이다. 한 아이템이 여러 카테고리에 중복으로 속할
 * 수 있어 리스트다(개수·임계값은 분석기가 정한다 — ai-mix 기본 최대 2개, 점수 0.65 이상).
 * 이름은 요청 시 넘긴 candidateCategories 안의 값이어야 하며, 저장 측(묶음 D)이 그
 * 워크스페이스의 category_id로 매핑한다. 매칭이 없으면 "기타"로 폴백한다.
 *
 * <p>summary는 본문이 있을 때만 채워진다 — title만으로 분류된 PARTIAL 아이템은 null.
 */
public record AiAnalysis(String title, String summary, List<String> categories) {

    /** 분석에 실패했거나 아직 분석기가 없을 때. 호출부는 이 값을 받으면 제목·요약·카테고리를 비워둔다. */
    public static AiAnalysis empty() {
        return new AiAnalysis(null, null, List.of());
    }

    public boolean isEmpty() {
        return title == null && summary == null && categories.isEmpty();
    }
}
