package com.ssafy.woojuin.domain.ai.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleBasedQueryPlannerTest {

    private final RuleBasedQueryPlanner planner = new RuleBasedQueryPlanner();

    @Test
    void 지시어와_질문꼬리를_걷어낸다() {
        assertThat(planner.plan("그 파스타집 어디였지?").keywords()).isEqualTo("파스타집");
    }

    @Test
    void 조사를_떼어낸다() {
        assertThat(planner.plan("을지로에서 갔던 맛집을 찾아줘").keywords()).contains("을지로", "맛집");
    }

    /** "의자"의 "의"처럼 조사처럼 생긴 글자가 단어의 일부인 경우를 잘라내면 안 된다. */
    @Test
    void 조사처럼_보여도_단어면_자르지_않는다() {
        assertThat(planner.plan("의자").keywords()).isEqualTo("의자");
    }

    @Test
    void 한_글자_토큰은_버린다() {
        assertThat(planner.plan("제주도 숙소 A").keywords()).isEqualTo("제주도 숙소");
    }

    @Test
    void 중복_토큰은_한_번만_남긴다() {
        assertThat(planner.plan("파스타 파스타 맛집").keywords()).isEqualTo("파스타 맛집");
    }

    /** 전부 걸러지면 빈손보다 원문이 낫다. */
    @Test
    void 전부_걸러지면_원문을_그대로_쓴다() {
        assertThat(planner.plan("그거 어디").keywords()).isEqualTo("그거 어디");
    }

    @Test
    void 빈_질문은_빈_키워드() {
        assertThat(planner.plan("   ").keywords()).isEmpty();
        assertThat(planner.plan(null).keywords()).isEmpty();
    }

    /** 규칙 기반은 AI가 아니므로 응답에서 구분되어야 한다. */
    @Test
    void aiPlanned는_항상_false() {
        assertThat(planner.plan("파스타집").aiPlanned()).isFalse();
    }

    /** 물음표가 검색어에 섞이면 부분일치가 깨진다. */
    @Test
    void 문장부호를_제거한다() {
        assertThat(planner.plan("파스타집?!").keywords()).isEqualTo("파스타집");
    }
}
