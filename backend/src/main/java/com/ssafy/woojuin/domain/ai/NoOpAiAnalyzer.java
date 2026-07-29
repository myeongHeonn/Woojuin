package com.ssafy.woojuin.domain.ai;

/**
 * {@link AiAnalyzer} 스텁 (ai-mix 사이드카를 끈 환경용).
 *
 * <p>항상 빈 분석 결과를 돌려준다 — 사이드카 없이도 저장·미리보기·OCR 파이프라인이
 * 동작·검증되게 하려는 것. 분석 결과가 비므로 요약·AI 제목 없이 저장되고 카테고리는
 * "기타" 폴백을 탄다.
 *
 * <p>{@link AiAnalyzerConfig}가 {@code woojuin.aimix.enabled=false}일 때 등록한다.
 */
public class NoOpAiAnalyzer implements AiAnalyzer {

    @Override
    public AiAnalysis analyze(AiAnalysisRequest request) {
        return AiAnalysis.empty();
    }
}
