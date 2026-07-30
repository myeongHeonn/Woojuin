package com.ssafy.woojuin.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiMixAnalyzerTest {

    @Mock AiMixClient client;
    @InjectMocks AiMixAnalyzer analyzer;

    private static final List<CategoryCandidate> CANDIDATES = List.of(
            new CategoryCandidate(1L, "학습·지식", "학습 관련"),
            new CategoryCandidate(2L, "기타", "그 외"));

    private AiAnalysisRequest request() {
        return new AiAnalysisRequest(AiSourceType.MEMO, "제목", "본문", CANDIDATES);
    }

    @Test
    void 제목_요약_생성_후_그_결과로_분류한다() {
        when(client.createTitleSummary(AiSourceType.MEMO, "제목", "본문"))
                .thenReturn(new AiMixClient.TitleSummary("다듬은 제목", "요약문"));
        when(client.classify("다듬은 제목", "요약문", CANDIDATES)).thenReturn(List.of("학습·지식"));

        AiAnalysis analysis = analyzer.analyze(request());

        assertThat(analysis.title()).isEqualTo("다듬은 제목");
        assertThat(analysis.summary()).isEqualTo("요약문");
        assertThat(analysis.categories()).containsExactly("학습·지식");
        // 분류는 원문이 아니라 생성된 제목·요약을 입력으로 받는다(묶음 F의 평가 설계).
        verify(client).classify("다듬은 제목", "요약문", CANDIDATES);
    }

    /** AI 실패가 이미 확보한 본문·미리보기를 무효화하면 안 된다(인터페이스 계약). */
    @Test
    void 제목_요약_생성이_실패하면_빈_결과를_반환한다() {
        when(client.createTitleSummary(any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("timeout"));

        assertThat(analyzer.analyze(request()).isEmpty()).isTrue();
    }

    @Test
    void 보낼_신호가_없으면_분류_없이_빈_결과다() {
        when(client.createTitleSummary(any(), any(), any())).thenReturn(null);

        assertThat(analyzer.analyze(request()).isEmpty()).isTrue();
        verify(client, never()).classify(anyString(), anyString(), anyList());
    }

    /** 부분 성공이 전부 실패보다 낫다 — 카테고리는 "기타" 폴백(저장 측)이 받아준다. */
    @Test
    void 분류만_실패하면_제목_요약은_살린다() {
        when(client.createTitleSummary(any(), anyString(), anyString()))
                .thenReturn(new AiMixClient.TitleSummary("다듬은 제목", "요약문"));
        when(client.classify(anyString(), anyString(), anyList()))
                .thenThrow(new IllegalStateException("503"));

        AiAnalysis analysis = analyzer.analyze(request());

        assertThat(analysis.title()).isEqualTo("다듬은 제목");
        assertThat(analysis.summary()).isEqualTo("요약문");
        assertThat(analysis.categories()).isEmpty();
    }
}
