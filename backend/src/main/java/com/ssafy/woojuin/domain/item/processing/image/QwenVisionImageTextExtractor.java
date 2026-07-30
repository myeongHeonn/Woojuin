package com.ssafy.woojuin.domain.item.processing.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.JpegWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Qwen3-VL(OpenRouter 경유)로 이미지에서 텍스트를 추출하는 {@link ImageTextExtractor} 구현.
 *
 * <p>모델과 프롬프트는 묶음 F의 이미지 모델 평가(ai-test 브랜치 {@code ai/ai-image}, 50장
 * 회귀 테스트)에서 확정된 것을 그대로 쓴다. 모델은 title·description·tags·ocr_text·objects를
 * 담은 JSON을 반환하고, 이 클래스는 그중 계약 문서(image-ai-contract.md)의
 * {@code classificationText} 형식으로 합친 텍스트를 돌려준다 — 이 값이 content로 저장돼
 * 통합 검색(ILIKE)과 AI 요약·분류({@code AiAnalysisRequest.text})의 입력이 된다.
 *
 * <p>전송 전에 긴 변 2048px 이내로 축소하고 JPEG(품질 90)로 다시 인코딩한다. F의 평가가
 * 이 전처리를 전제로 이뤄졌고, 원본 그대로면 20MB급 사진에서 요청이 무거워진다. 리사이즈가
 * 실패하면(깨진 이미지 등) 원본 바이트로 폴백하지 않고 그대로 실패시킨다 — 모델도 못 읽을
 * 가능성이 높다.
 *
 * <p>인터페이스 계약대로 어떤 실패든 예외 대신 null을 반환한다. 호출부(ImageItemProcessor)가
 * null이면 PARTIAL로 마무리한다.
 */
@Slf4j
public class QwenVisionImageTextExtractor implements ImageTextExtractor {

    private static final int MAX_EDGE = 2048;
    private static final int JPEG_QUALITY = 90;

    private final RestClient restClient;
    private final String model;
    private final String prompt;
    private final ObjectMapper objectMapper;

    public QwenVisionImageTextExtractor(String baseUrl, String apiKey, String model,
            Duration timeout, String prompt, ObjectMapper objectMapper) {
        this.model = model;
        this.prompt = prompt;
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

    @Override
    public String extract(byte[] imageBytes) {
        try {
            String content = complete(toDataUri(resize(imageBytes)));
            if (content == null || content.isBlank()) {
                return null;
            }
            String text = toClassificationText(objectMapper.readTree(stripCodeFence(content)));
            return text.isBlank() ? null : text;
        } catch (Exception e) {
            log.info("이미지 텍스트 추출 실패: cause={}", e.toString());
            return null;
        }
    }

    /** 긴 변 {@value MAX_EDGE}px 이내로 축소하고 JPEG로 재인코딩한다(EXIF 방향 보정 포함). */
    private byte[] resize(byte[] original) throws Exception {
        return ImmutableImage.loader().fromBytes(original)
                .max(MAX_EDGE, MAX_EDGE)
                .bytes(new JpegWriter(JPEG_QUALITY, false));
    }

    private String toDataUri(byte[] jpegBytes) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpegBytes);
    }

    /**
     * OpenAI 호환 vision 요청. 텍스트 전용인 {@code ChatCompletionClient}와 달리 content가
     * 파트 배열(text + image_url)이라 별도 구현이다. 응답을 String으로 받아 직접 파싱하는
     * 방어는 동일하게 유지한다(프록시가 Content-Type을 이상하게 주는 경우 대비).
     */
    private String complete(String imageDataUri) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url",
                                        "image_url", Map.of("url", imageDataUri))))),
                "response_format", Map.of("type", "json_object"));

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
        try {
            JsonNode choices = objectMapper.readTree(raw).path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            JsonNode content = choices.get(0).path("message").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            throw new IllegalStateException("vision 응답 파싱 실패", e);
        }
    }

    /**
     * 모델 JSON을 계약 문서의 classificationText로 합친다. 라벨·순서·빈 값 줄 생략까지
     * F의 {@code image_service.integration.build_classification_text}와 동일하다 —
     * F가 이 형식으로 분류 품질을 검증했으므로 임의로 바꾸지 않는다.
     */
    String toClassificationText(JsonNode result) {
        StringJoiner joiner = new StringJoiner("\n");
        appendSection(joiner, "이미지 설명", result.path("description").asText(""));
        appendSection(joiner, "OCR 텍스트", result.path("ocr_text").asText(""));
        appendSection(joiner, "태그", joinArray(result.path("tags")));
        appendSection(joiner, "주요 객체", joinArray(result.path("objects")));
        return joiner.toString();
    }

    private void appendSection(StringJoiner joiner, String label, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(label + ": " + value.strip());
        }
    }

    private String joinArray(JsonNode array) {
        if (!array.isArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        array.forEach(node -> {
            String value = node.asText("");
            if (!value.isBlank()) {
                values.add(value.strip());
            }
        });
        return String.join(", ", values);
    }

    /** 모델이 response_format을 무시하고 ```json 펜스로 감싼 경우를 걷어낸다. */
    static String stripCodeFence(String content) {
        String trimmed = content.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int fenceEnd = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || fenceEnd <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, fenceEnd).strip();
    }
}
