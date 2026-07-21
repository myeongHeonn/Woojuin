package com.ssafy.woojuin.domain.ai;

/**
 * AI 분석 입력. 세 아이템 타입이 모두 "텍스트"로 수렴한 뒤 이 형태로 들어온다.
 * URL은 본문 추출 결과(묶음 D), 이미지는 OCR 결과, 메모는 사용자가 쓴 내용 그대로.
 *
 * @param title 아이템 제목. 본문이 짧을 때 분류 정확도를 올려주므로 있으면 넣는다 (nullable)
 * @param text  분석 대상 본문. 비어 있으면 안 된다
 */
public record AiAnalysisRequest(String title, String text) {
}
