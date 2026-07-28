package com.ssafy.woojuin.domain.ai.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * OpenAI 호환 Chat Completions 클라이언트.
 *
 * <p>base-url만 바꾸면 세 경로를 모두 커버한다 — 팀이 쓰는 SSAFY GMS 프록시
 * ({@code https://gms.ssafy.io/gmsapi/api.openai.com/v1}), OpenAI 직접 호출, 그리고
 * 로컬 Ollama({@code http://localhost:11434/v1})다. 묶음 F의 모델 비교에서 로컬 Qwen3와
 * GMS의 GPT-5 mini가 모두 후보로 남아 있어, 특정 프로바이더에 묶이지 않게 했다.
 *
 * <p>temperature·seed는 보내지 않는다 — GPT-5 계열이 받지 않는다(F의 비교 보고서 기준).
 */
public class ChatCompletionClient {

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public ChatCompletionClient(String baseUrl, String apiKey, String model, Duration timeout,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(new RestTemplateBuilder()
                        .setConnectTimeout(timeout)
                        .setReadTimeout(timeout)
                        .buildRequestFactory())
                .build();
    }

    /**
     * @return 모델이 돌려준 content 문자열. 응답이 비었으면 null.
     * @throws RuntimeException 네트워크·인증·서버·파싱 오류. 호출부가 폴백을 책임진다.
     */
    public String complete(String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                // JSON만 뱉게 강제한다. 프록시가 이 필드를 모르면 무시될 뿐이라
                // 파싱 쪽에서 코드펜스까지 걷어내는 방어를 함께 둔다.
                "response_format", Map.of("type", "json_object"));

        // 응답을 String으로 받아 직접 파싱한다. GMS 프록시가 JSON을
        // Content-Type: application/octet-stream 으로 내려보내는 경우가 있어(간헐적),
        // 타입 지정 역직렬화에 맡기면 "no suitable HttpMessageConverter"로 실패한다.
        // String 변환은 어떤 Content-Type이든 통하므로 프록시 구현에 휘둘리지 않는다.
        String raw = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        if (raw == null || raw.isBlank()) {
            return null;
        }

        JsonNode choices = objectMapper.readTree(raw).path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode content = choices.get(0).path("message").path("content");
        return content.isMissingNode() || content.isNull() ? null : content.asText();
    }
}
