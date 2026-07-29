package com.ssafy.woojuin.domain.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * ai-mix 사이드카(ai/ai-mix, 묶음 F 산출물) HTTP 클라이언트. 제목·요약 생성, 카테고리
 * 분류, 카테고리 설명 생성을 감싼다. 임베딩·3차원 좌표 엔드포인트도 같은 서비스에 있지만
 * 그건 다음 작업(임베딩 저장) 몫이라 여기 없다.
 *
 * <p>ai-mix 입력 모델은 {@code extra="forbid"}(모르는 키 거부)라 필드를 계약 그대로만
 * 보내고, 길이 제한(제목 300/1000자, 본문 50000자, 설명 5000자 등)도 클라이언트에서
 * 잘라 보낸다 — 422로 통째로 실패하는 것보다 잘린 입력으로 분석되는 쪽이 낫다.
 *
 * <p>실패 정책: 예외를 그대로 던진다. 폴백(빈 결과)은 {@link AiMixAnalyzer}가 책임진다.
 */
public class AiMixClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiMixClient(String baseUrl, Duration timeout, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new RestTemplateBuilder()
                        .setConnectTimeout(timeout)
                        .setReadTimeout(timeout)
                        .buildRequestFactory())
                .build();
    }

    /** 제목·요약 결과. */
    public record TitleSummary(String title, String summary) {
    }

    /**
     * 제목·요약 생성. 타입별 엔드포인트({@code /v1/title-summary/memo|url|image})가 입력
     * 스키마도 달라 여기서 변환한다. 보낼 신호가 하나도 없으면 호출 없이 null을 반환한다.
     */
    public TitleSummary createTitleSummary(AiSourceType sourceType, String title, String text) {
        Map<String, Object> payload = switch (sourceType) {
            // text가 필수(min 1)다. 메모는 본문이 항상 있지만 방어적으로 title 폴백을 둔다.
            case MEMO -> {
                String body = firstNonBlank(text, title);
                yield body == null ? null : payloadOf(
                        "title", truncate(title, 300),
                        "text", truncate(body, 50_000));
            }
            case URL -> (isBlank(title) && isBlank(text)) ? null : payloadOf(
                    "title", truncate(title, 1_000),
                    "content", truncate(text, 50_000));
            // 이미지 추출기(QwenVisionImageTextExtractor)가 설명·OCR·태그를 한 텍스트로
            // 합쳐 내보내므로 description 필드 하나에 싣는다.
            case IMAGE -> (isBlank(title) && isBlank(text)) ? null : payloadOf(
                    "visionTitle", truncate(title, 300),
                    "description", truncate(text, 5_000));
        };
        if (payload == null) {
            return null;
        }

        JsonNode data = post("/v1/title-summary/" + sourceType.pathSegment(), payload);
        String resultTitle = textOrNull(data.path("title"));
        String summary = textOrNull(data.path("summary"));
        return (resultTitle == null && summary == null) ? null : new TitleSummary(resultTitle, summary);
    }

    /**
     * 카테고리 분류. 임계값(0.65)·최대 개수(2)는 ai-mix 설정이 정한다.
     *
     * @return 선택된 카테고리 <b>이름</b> 목록(점수 내림차순). 후보가 없으면 호출 없이 빈 목록.
     */
    public List<String> classify(String title, String summary, List<CategoryCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> candidatePayload = candidates.stream()
                .limit(100)   // ai-mix 계약 상한. 워크스페이스에 100개 넘게 만드는 경우는 사실상 없다
                .map(candidate -> Map.<String, Object>of(
                        "categoryId", candidate.id(),
                        "name", truncate(candidate.name(), 100),
                        // 설명이 아직 없으면(생성 전 사용자 카테고리) 이름으로 폴백 — 빈 값은 422다
                        "description", truncate(firstNonBlank(candidate.description(), candidate.name()), 500)))
                .toList();

        // title·summary는 필수(min 1)라 비면 422다. 제목만 있는 PARTIAL 아이템도 분류는
        // 시도해야 하므로 서로 폴백시킨다.
        JsonNode data = post("/v1/categories/classify", Map.of(
                "title", truncate(firstNonBlank(firstNonBlank(title, summary), "제목 없음"), 100),
                "summary", truncate(firstNonBlank(firstNonBlank(summary, title), "내용 없음"), 1_000),
                "candidateCategories", candidatePayload));

        List<String> names = new ArrayList<>();
        data.path("categories").forEach(node -> {
            String name = textOrNull(node.path("name"));
            if (name != null) {
                names.add(name);
            }
        });
        return names;
    }

    /**
     * 카테고리 설명 생성(사용자 카테고리용).
     *
     * @return 생성된 설명. 응답에 설명이 없으면 null.
     */
    public String createCategoryDescription(Long categoryId, String name) {
        JsonNode data = post("/v1/categories/description", Map.of(
                "categoryId", categoryId,
                "categoryName", truncate(name, 100)));
        return textOrNull(data.path("description"));
    }

    /** 공통 POST. 공통 응답 형식 {@code {status, message, data}}의 data 노드를 돌려준다. */
    private JsonNode post(String path, Map<String, Object> body) {
        String raw = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("ai-mix 응답이 비어 있음: " + path);
        }
        try {
            return objectMapper.readTree(raw).path("data");
        } catch (Exception e) {
            throw new IllegalStateException("ai-mix 응답 파싱 실패: " + path, e);
        }
    }

    /** key-value 쌍으로 맵을 만들되 null 값 키는 뺀다 — ai-mix는 null 대신 키 생략을 기대한다(Optional 필드). */
    private static Map<String, Object> payloadOf(Object... pairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                payload.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return payload;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : (!isBlank(second) ? second : null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String textOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText("");
        return text.isBlank() ? null : text;
    }
}
