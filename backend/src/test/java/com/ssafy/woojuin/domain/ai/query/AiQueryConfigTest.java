package com.ssafy.woojuin.domain.ai.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 키가 있으면 LLM, 없으면 규칙 기반. 키 이름은 OPENAI_API_KEY 하나뿐이다(값은 GMS 키).
 *
 * <p>{@code OPENAI_API_KEY=} 처럼 빈 줄만 남은 경우가 "키 있음"으로 잡히면 LLM 플래너가
 * 올라가 매 검색마다 401을 맞고 폴백하는(=느리고 조용히 망가진) 상태가 된다.
 * 그래서 blank까지 본다.
 */
class AiQueryConfigTest {

    private final AiQueryConfig config = new AiQueryConfig();
    private final RuleBasedQueryPlanner ruleBased = new RuleBasedQueryPlanner();

    private AiQueryPlanner plannerFor(String apiKey) {
        return config.aiQueryPlanner(ruleBased, new ObjectMapper(), apiKey,
                "https://example.com/v1", "test-model", 1000L);
    }

    @Test
    void 키가_있으면_LLM() {
        assertThat(plannerFor("key")).isInstanceOf(LlmQueryPlanner.class);
    }

    @Test
    void 키가_없으면_규칙기반() {
        assertThat(plannerFor("")).isSameAs(ruleBased);
        assertThat(plannerFor(null)).isSameAs(ruleBased);
    }

    /** `OPENAI_API_KEY=` 빈 줄이나 공백만 남은 경우도 "없음"으로 본다. */
    @Test
    void 공백뿐인_키는_없는_것으로_본다() {
        assertThat(plannerFor("   ")).isSameAs(ruleBased);
    }
}
