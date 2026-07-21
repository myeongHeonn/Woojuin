package com.ssafy.woojuin.domain.ai;

import java.util.List;

/**
 * AI 분석 결과.
 *
 * <p>category는 카테고리 <b>이름</b>이다. categories 테이블이 아직 없어서 id로 못 준다.
 * 카테고리 도메인이 생기면 여기서 이름→id 매핑을 붙이고, 그 전까지 items.category_id는
 * null로 둔다 (팀 합의).
 */
public record AiAnalysis(String summary, String category, List<String> tags) {

    /** 분석에 실패했거나 아직 분석기가 없을 때. 호출부는 이 값을 받으면 PARTIAL로 확정한다. */
    public static AiAnalysis empty() {
        return new AiAnalysis(null, null, List.of());
    }

    public boolean isEmpty() {
        return summary == null && category == null && tags.isEmpty();
    }
}
