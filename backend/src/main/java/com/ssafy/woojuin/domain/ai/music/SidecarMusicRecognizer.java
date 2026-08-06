package com.ssafy.woojuin.domain.ai.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * 사이드카 응답을 {@link MusicRecognizer} 계약으로 옮긴다 — <b>못 찾음(빈 결과)과
 * 실패(예외)를 가르는 것</b>이 이 클래스의 일이다.
 *
 * <p>사이드카 계약: {@code {"status":200,"data":{"found":true,"title":...,"link":...}}}.
 * {@code found} 가 거짓이면 못 찾은 것이고, 상태코드가 2xx 가 아니면 실패다.
 */
@Slf4j
public class SidecarMusicRecognizer implements MusicRecognizer {

    private final SidecarMusicClient client;
    private final ObjectMapper objectMapper;

    public SidecarMusicRecognizer(SidecarMusicClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<RecognizedSong> recognize(byte[] audio, String filename) {
        if (audio == null || audio.length == 0) {
            throw new MusicRecognitionFailedException("오디오가 비어 있습니다");
        }

        String raw;
        try {
            raw = client.recognizeRaw(audio, filename);
        } catch (Exception e) {
            log.info("노래 인식 호출 실패: bytes={}, cause={}", audio.length, e.toString());
            throw new MusicRecognitionFailedException("노래 인식 호출이 실패했습니다", e);
        }
        if (raw == null || raw.isBlank()) {
            throw new MusicRecognitionFailedException("노래 인식 응답이 비었습니다");
        }

        JsonNode data;
        try {
            data = objectMapper.readTree(raw).path("data");
        } catch (Exception e) {
            throw new MusicRecognitionFailedException("노래 인식 응답을 읽을 수 없습니다", e);
        }
        if (!data.path("found").asBoolean(false)) {
            return Optional.empty();
        }

        String link = text(data, "link");
        if (link == null) {
            // 링크 없는 결과는 URL 아이템으로 저장할 수 없다 — 사이드카가 이미 걸러내지만
            // 계약을 여기서도 지킨다(구현이 둘이고 폴백이 다른 응답을 줄 수 있다)
            log.info("링크 없는 인식 결과 — 못 찾은 것으로 본다: title={}", text(data, "title"));
            return Optional.empty();
        }
        return Optional.of(new RecognizedSong(
                text(data, "title"), text(data, "artist"), link, text(data, "coverUrl")));
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
