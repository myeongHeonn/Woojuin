package com.ssafy.woojuin.domain.ai;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * ai-mix 사이드카를 쓰는 {@link AiAnalyzer} 구현. 두 단계로 호출한다 —
 * ① 제목·요약 생성 → ② 그 결과(제목+요약)로 카테고리 분류. 분류가 원문이 아니라
 * 요약을 입력으로 받는 건 묶음 F의 평가 설계다(긴 원문보다 요약이 분류 정확도가 높았다).
 *
 * <p>실패 정책(인터페이스 계약): 어떤 실패도 예외로 새지 않고, ①이 실패하면
 * {@link AiAnalysis#empty()}, ②만 실패하면 제목·요약은 살리고 카테고리만 비운다 —
 * 부분 성공이 전부 실패보다 낫고, 카테고리는 "기타" 폴백(저장 측)이 받아준다.
 */
@Slf4j
public class AiMixAnalyzer implements AiAnalyzer {

    private final AiMixClient client;

    public AiMixAnalyzer(AiMixClient client) {
        this.client = client;
    }

    @Override
    public AiAnalysis analyze(AiAnalysisRequest request) {
        AiMixClient.TitleSummary titleSummary;
        try {
            titleSummary = client.createTitleSummary(
                    request.sourceType(), request.title(), request.text());
        } catch (Exception e) {
            log.warn("ai-mix 제목·요약 생성 실패: sourceType={}, cause={}", request.sourceType(), e.toString());
            return AiAnalysis.empty();
        }
        if (titleSummary == null) {
            // 보낼 신호가 없었거나(제목·본문 모두 빈 값) 응답이 비었음 — 분류도 근거가 없다.
            return AiAnalysis.empty();
        }

        List<String> categories;
        try {
            categories = client.classify(
                    titleSummary.title(), titleSummary.summary(), request.candidateCategories());
        } catch (Exception e) {
            log.warn("ai-mix 카테고리 분류 실패(제목·요약은 유지): cause={}", e.toString());
            categories = List.of();
        }
        return new AiAnalysis(titleSummary.title(), titleSummary.summary(), categories);
    }
}
