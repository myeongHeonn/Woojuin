package com.ssafy.woojuin.domain.ai.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM에게 자연어 질의를 검색어로 바꾸게 하는 플래너. API 키가 설정됐을 때만 등록된다
 * ({@link AiQueryConfig}).
 *
 * <p>어떤 실패도 예외로 흘리지 않고 규칙 기반 결과로 폴백한다 — LLM이 죽었다고 검색창이
 * 에러를 뱉으면 안 된다. 폴백했다는 사실은 {@code aiPlanned=false}로 응답에 드러난다.
 */
@Slf4j
public class LlmQueryPlanner implements AiQueryPlanner {

    /**
     * 키워드 검색이 부분일치 AND라는 점을 프롬프트에 명시해야 모델이 과하게 많은 단어를
     * 뱉지 않는다 — 토큰이 늘수록 AND 조건이 빡세져서 0건이 되기 쉽다.
     */
    private static final String SYSTEM_PROMPT = """
            너는 개인 스크랩북의 검색어 생성기다. 사용자의 질문에서 저장된 스크랩을 찾기 위한
            검색 키워드만 뽑아라.

            규칙:
            - 명백한 오타는 반드시 올바른 표기로 고쳐라. 한글은 한 음절만 어긋난 오타가 흔하다.
              예: 울지로 -> 을지로, 파스카 -> 파스타, 리엑트 -> 리액트, 재주도 -> 제주도
            - 지시어("그", "저"), 질문 꼬리("어디였지", "찾아줘")처럼 저장된 본문에 없을 말은 빼라.
            - 조사는 떼고 명사 위주로 남겨라.
            - 사용자가 말하지 않은 단어를 새로 지어내지 마라. 동의어·관련어를 덧붙이지 마라.
              검색은 모든 키워드를 포함하는 문서를 찾는 AND 방식이라, 없는 단어를 하나만
              끼워 넣어도 결과가 통째로 어긋난다.
            - 사용자가 쓴 말 중 핵심 명사만 남기고 최대 4개까지 써라.
            - 뽑을 게 없으면 원문에서 명사만 남겨라.

            반드시 아래 JSON 형식으로만 답하라. 다른 말은 하지 마라.
            {"keywords": "키워드1 키워드2"}
            """;

    private final ChatCompletionClient client;
    private final RuleBasedQueryPlanner fallback;
    private final ObjectMapper objectMapper;

    public LlmQueryPlanner(ChatCompletionClient client, RuleBasedQueryPlanner fallback,
            ObjectMapper objectMapper) {
        this.client = client;
        this.fallback = fallback;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiQueryPlan plan(String question) {
        if (question == null || question.isBlank()) {
            return AiQueryPlan.byRule("");
        }
        try {
            String keywords = parseKeywords(client.complete(SYSTEM_PROMPT, question));
            if (keywords != null && !keywords.isBlank()) {
                return AiQueryPlan.byAi(keywords.trim());
            }
            log.info("LLM이 빈 키워드를 반환해 규칙 기반으로 폴백");
        } catch (Exception e) {
            // 키 오류·타임아웃·프록시 장애 전부 여기로 온다. 검색 자체는 계속돼야 한다.
            log.warn("LLM 질의 해석 실패, 규칙 기반으로 폴백: cause={}", e.toString());
        }
        return fallback.plan(question);
    }

    /**
     * 모델이 {@code ```json ... ```}로 감싸 보내는 경우가 있어 코드펜스를 걷어낸 뒤 파싱한다
     * (response_format을 지원하지 않는 프록시·로컬 모델 대비).
     */
    private String parseKeywords(String content) throws Exception {
        if (content == null || content.isBlank()) {
            return null;
        }
        String json = content.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        JsonNode node = objectMapper.readTree(json).get("keywords");
        return node == null || node.isNull() ? null : node.asText();
    }
}
