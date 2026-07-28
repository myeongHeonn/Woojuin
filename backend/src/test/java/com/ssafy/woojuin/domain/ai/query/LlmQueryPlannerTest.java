package com.ssafy.woojuin.domain.ai.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmQueryPlannerTest {

    @Mock
    private ChatCompletionClient client;

    private LlmQueryPlanner planner;

    @BeforeEach
    void setUp() throws Exception {
        planner = new LlmQueryPlanner(client, new RuleBasedQueryPlanner(), new ObjectMapper());
    }

    @Test
    void LLM이_준_키워드를_쓰고_aiPlanned를_표시한다() throws Exception {
        when(client.complete(anyString(), anyString())).thenReturn("{\"keywords\": \"을지로 파스타\"}");

        AiQueryPlan plan = planner.plan("울지로 파스카집 어디였지?");

        assertThat(plan.keywords()).isEqualTo("을지로 파스타");
        assertThat(plan.aiPlanned()).isTrue();
    }

    /** response_format을 지원하지 않는 프록시·로컬 모델은 코드펜스로 감싸 보낸다. */
    @Test
    void 코드펜스로_감싼_JSON도_파싱한다() throws Exception {
        when(client.complete(anyString(), anyString()))
                .thenReturn("```json\n{\"keywords\": \"제주도 숙소\"}\n```");

        assertThat(planner.plan("제주도 숙소 찾아줘").keywords()).isEqualTo("제주도 숙소");
    }

    /** LLM이 죽어도 검색창이 에러를 뱉으면 안 된다. */
    @Test
    void 호출이_실패하면_규칙기반으로_폴백한다() throws Exception {
        when(client.complete(anyString(), anyString())).thenThrow(new RuntimeException("timeout"));

        AiQueryPlan plan = planner.plan("그 파스타집 어디였지?");

        assertThat(plan.keywords()).isEqualTo("파스타집");
        assertThat(plan.aiPlanned()).isFalse();
    }

    @Test
    void 응답이_JSON이_아니면_폴백한다() throws Exception {
        when(client.complete(anyString(), anyString())).thenReturn("죄송합니다, 도와드릴 수 없습니다");

        AiQueryPlan plan = planner.plan("그 파스타집 어디였지?");

        assertThat(plan.keywords()).isEqualTo("파스타집");
        assertThat(plan.aiPlanned()).isFalse();
    }

    @Test
    void 빈_키워드를_받으면_폴백한다() throws Exception {
        when(client.complete(anyString(), anyString())).thenReturn("{\"keywords\": \"\"}");

        AiQueryPlan plan = planner.plan("그 파스타집 어디였지?");

        assertThat(plan.keywords()).isEqualTo("파스타집");
        assertThat(plan.aiPlanned()).isFalse();
    }

    /** 질문이 비면 LLM을 부를 이유가 없다(토큰 낭비). */
    @Test
    void 빈_질문은_LLM을_호출하지_않는다() throws Exception {
        AiQueryPlan plan = planner.plan("  ");

        assertThat(plan.keywords()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(client);
    }
}
