package com.ssafy.woojuin.domain.ai.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * AudD 정식 API 기반 예비 인식기 — 기본 경로(비공식 shazamio)가 깨졌을 때만 불린다
 * ({@link FallbackMusicRecognizer}).
 *
 * <p>계약: {@code POST https://api.audd.io/} 에 {@code api_token} 과 multipart {@code file}.
 * 응답의 {@code result.song_link} 가 저장에 쓸 링크다(문서 기준 — 라이브 검증은 키 발급
 * 후에 한다. 그래서 파싱은 필드가 없을 때 조용히 "못 찾음"으로 떨어지지 않고 링크가 없으면
 * 빈 결과가 되도록만 해 뒀다).
 *
 * <p><b>과금 주의.</b> 무료 300건 뒤에는 초과분이 자동 청구되고 문서에 하드 리밋이 없다.
 * 그래서 (1) 기본 경로 실패에만 불리고, (2) "못 찾음"에는 재시도하지 않는다
 * ({@link FallbackMusicRecognizer} 주석 참고).
 */
@Slf4j
public class AuddMusicRecognizer implements MusicRecognizer {

    private static final String BASE_URL = "https://api.audd.io";

    private final RestClient restClient;
    private final String apiToken;
    private final ObjectMapper objectMapper;

    public AuddMusicRecognizer(String apiToken, Duration timeout, ObjectMapper objectMapper) {
        this.apiToken = apiToken;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        factory.setOutputStreaming(false);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .build();
    }

    @Override
    public Optional<RecognizedSong> recognize(byte[] audio, String filename) {
        if (audio == null || audio.length == 0) {
            throw new MusicRecognitionFailedException("오디오가 비어 있습니다");
        }

        String boundary = "woojuin" + UUID.randomUUID().toString().replace("-", "");
        String raw;
        try {
            raw = restClient.post()
                    .uri("/")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .body(multipartBody(boundary, audio, filename))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.info("예비 노래 인식(AudD) 호출 실패: cause={}", e.toString());
            throw new MusicRecognitionFailedException("예비 노래 인식 호출이 실패했습니다", e);
        }
        if (raw == null || raw.isBlank()) {
            throw new MusicRecognitionFailedException("예비 노래 인식 응답이 비었습니다");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new MusicRecognitionFailedException("예비 노래 인식 응답을 읽을 수 없습니다", e);
        }
        // AudD 는 실패도 200 으로 주고 status 로 구분한다
        if (!"success".equals(root.path("status").asText(""))) {
            throw new MusicRecognitionFailedException(
                    "예비 노래 인식이 거부됐습니다: " + root.path("error").path("error_message").asText(""));
        }
        JsonNode result = root.path("result");
        if (result.isMissingNode() || result.isNull()) {
            // 못 찾은 것 — 오류가 아니다
            return Optional.empty();
        }
        String link = text(result, "song_link");
        if (link == null) {
            log.info("링크 없는 예비 인식 결과 — 못 찾은 것으로 본다: title={}", text(result, "title"));
            return Optional.empty();
        }
        return Optional.of(new RecognizedSong(
                text(result, "title"), text(result, "artist"), link, null));
    }

    /** curl 의 {@code -F} 와 같은 본문(SidecarMusicClient 와 같은 방식). */
    byte[] multipartBody(String boundary, byte[] audio, String filename) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "--" + boundary + "\r\n");
        writeAscii(out, "Content-Disposition: form-data; name=\"api_token\"\r\n\r\n");
        writeUtf8(out, apiToken);
        writeAscii(out, "\r\n--" + boundary + "\r\n");
        writeUtf8(out,
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
        writeAscii(out, "Content-Type: audio/wav\r\n\r\n");
        write(out, audio);
        writeAscii(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String text) {
        write(out, text.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeUtf8(ByteArrayOutputStream out, String text) {
        write(out, text.getBytes(StandardCharsets.UTF_8));
    }

    private void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String asText = value.asText().trim();
        return asText.isEmpty() ? null : asText;
    }
}
