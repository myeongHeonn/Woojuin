package com.ssafy.woojuin.domain.ai.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * API 키가 설정돼 있을 때만 LLM 플래너를 기본 빈으로 올린다. 키가 없으면
 * {@link RuleBasedQueryPlanner}가 그대로 쓰여 AI 모드가 "키워드 정리" 수준으로 동작한다 —
 * 키 없는 로컬 환경에서도 앱이 뜨고 엔드포인트가 200을 주게 하려는 것이다.
 *
 * <p>{@code @Primary}로 올리는 이유: RuleBasedQueryPlanner도 {@code @Component}라
 * AiQueryPlanner 구현이 둘이 된다. LLM 쪽이 폴백으로 규칙 기반을 주입받아 쓰므로
 * 둘 다 빈으로 살아 있어야 한다.
 */
@Slf4j
@Configuration
public class AiQueryConfig {

    /**
     * 키는 {@code OPENAI_API_KEY} 하나만 본다(팀 합의). 실제로 담기는 값은 SSAFY GMS 키지만,
     * GMS가 OpenAI 호환 프록시라 변수 이름을 하나로 통일하는 쪽을 택했다 — 이름을 둘로 두면
     * "어디에 넣어야 하나"만 헷갈린다.
     */
    @Bean
    @Primary
    public AiQueryPlanner aiQueryPlanner(
            RuleBasedQueryPlanner ruleBasedQueryPlanner,
            ObjectMapper objectMapper,
            @Value("${OPENAI_API_KEY:}") String openAiApiKey,
            @Value("${woojuin.ai.base-url}") String baseUrl,
            @Value("${woojuin.ai.model}") String model,
            @Value("${woojuin.ai.timeout-ms}") long timeoutMs) {

        String apiKey = openAiApiKey == null ? "" : openAiApiKey.trim();

        if (apiKey.isBlank()) {
            log.info("AI 질의 해석: API 키가 없어 규칙 기반으로 동작합니다 "
                    + "(오타 교정 불가). .env에 OPENAI_API_KEY를 넣으면 LLM이 활성화됩니다");
            return ruleBasedQueryPlanner;
        }

        log.info("AI 질의 해석: LLM 활성화 (baseUrl={}, model={})", baseUrl, model);
        ChatCompletionClient client = new ChatCompletionClient(
                baseUrl, apiKey, model, Duration.ofMillis(timeoutMs), objectMapper);
        return new LlmQueryPlanner(client, ruleBasedQueryPlanner, objectMapper);
    }
}
