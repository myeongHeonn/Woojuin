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
 * AudD 정식 API 기반 노래 인식 — 워치 "노래 찾기"의 유일한 경로다.
 *
 * <p>계약: {@code POST https://api.audd.io/} 에 {@code api_token} 과 multipart {@code file}.
 * 응답의 {@code result.song_link} 가 저장에 쓸 링크다 — 라이브로 확인했다(전영호 - Butter-Fly
 * 12초 WAV → 2.3초에 {@code https://lis.tn/...} 단축 링크). 그 링크를 URL 아이템으로 저장하니
 * 크롤이 제목("Butter-Fly by 전영호")과 썸네일까지 채웠다.
 *
 * <p><b>과금 주의.</b> 무료 300건 뒤에는 초과분이 자동 청구되고 문서에 하드 리밋이 없다.
 * 그래서 <b>재시도하지 않는다</b> — 못 찾은 것은 정상적인 답이므로 같은 오디오를 다시 올리지
 * 않고, 실패도 그대로 올려 화면이 사용자에게 다시 시도할지 맡긴다(자동 재시도는 사용자가
 * 모르는 채로 요청을 두 배로 쓴다).
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
            log.info("노래 인식 호출 실패: cause={}", e.toString());
            throw new MusicRecognitionFailedException("노래 인식 호출이 실패했습니다", e);
        }
        if (raw == null || raw.isBlank()) {
            throw new MusicRecognitionFailedException("노래 인식 응답이 비었습니다");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new MusicRecognitionFailedException("노래 인식 응답을 읽을 수 없습니다", e);
        }
        // AudD 는 실패도 200 으로 주고 status 로 구분한다
        if (!"success".equals(root.path("status").asText(""))) {
            throw new MusicRecognitionFailedException(
                    "노래 인식이 거부됐습니다: " + root.path("error").path("error_message").asText(""));
        }
        JsonNode result = root.path("result");
        if (result.isMissingNode() || result.isNull()) {
            // 못 찾은 것 — 오류가 아니다
            return Optional.empty();
        }
        String link = text(result, "song_link");
        if (link == null) {
            log.info("링크 없는 인식 결과 — 못 찾은 것으로 본다: title={}", text(result, "title"));
            return Optional.empty();
        }
        return Optional.of(new RecognizedSong(
                text(result, "title"), text(result, "artist"), link, null));
    }

    /** curl 의 {@code -F} 와 같은 본문. 줄바꿈은 반드시 CRLF 다(WhisperClient 와 같은 이유). */
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
