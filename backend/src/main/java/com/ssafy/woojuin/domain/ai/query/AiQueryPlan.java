package com.ssafy.woojuin.domain.ai.query;

/**
 * 자연어 질의를 키워드 검색에 넘길 수 있는 형태로 바꾼 결과.
 *
 * @param keywords 공백으로 구분된 검색어. 기존 키워드 검색이 그대로 받아 토큰으로 쪼갠다.
 *                 오타 교정·동의어 확장이 이미 적용된 상태다.
 * @param aiPlanned LLM이 만든 계획인지(true), 키가 없거나 호출이 실패해 규칙 기반으로
 *                 폴백한 것인지(false). 응답에 실어 클라이언트가 "AI가 해석했음"을 구분한다.
 */
public record AiQueryPlan(String keywords, boolean aiPlanned) {

    public static AiQueryPlan byAi(String keywords) {
        return new AiQueryPlan(keywords, true);
    }

    public static AiQueryPlan byRule(String keywords) {
        return new AiQueryPlan(keywords, false);
    }

    public boolean isEmpty() {
        return keywords == null || keywords.isBlank();
    }
}
