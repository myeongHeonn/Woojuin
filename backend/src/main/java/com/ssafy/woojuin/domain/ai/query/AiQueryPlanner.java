package com.ssafy.woojuin.domain.ai.query;

/**
 * 자연어 질의 → 검색 키워드. AI 모드 검색의 유일한 AI 의존 지점이다.
 *
 * <p>"그 파스타집 어디였지?" 같은 문장에서 검색에 쓸 말만 뽑고, 가능하면 오타를 고치고
 * 관련어까지 넓힌다. 키워드 검색이 원리상 못 하는 부분(오타·표현 차이)을 여기서 흡수하는
 * 것이 AI 모드의 존재 이유다.
 *
 * <p>구현체는 실패 시 예외를 던지지 말고 규칙 기반 결과로 폴백할 것 — AI가 죽어도 검색
 * 자체는 되어야 한다({@link AiAnalyzer}와 같은 계약).
 *
 * @see com.ssafy.woojuin.domain.ai.AiAnalyzer
 */
public interface AiQueryPlanner {

    AiQueryPlan plan(String question);
}
