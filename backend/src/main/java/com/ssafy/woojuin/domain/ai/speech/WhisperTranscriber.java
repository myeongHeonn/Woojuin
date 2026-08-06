package com.ssafy.woojuin.domain.ai.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * whisper 응답을 {@link SpeechTranscriber} 계약으로 옮긴다 — <b>무음(빈 문자열)과 실패(예외)를
 * 가르는 것</b>이 이 클래스의 일이다. 전송은 {@link WhisperClient} 가 한다.
 */
@Slf4j
public class WhisperTranscriber implements SpeechTranscriber {

    private final WhisperClient client;
    private final ObjectMapper objectMapper;

    public WhisperTranscriber(WhisperClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String transcribe(byte[] audio, String filename) {
        if (audio == null || audio.length == 0) {
            throw new TranscriptionFailedException("오디오가 비어 있습니다");
        }

        String raw;
        try {
            raw = client.transcribeRaw(audio, filename);
        } catch (Exception e) {
            log.info("받아쓰기 호출 실패: bytes={}, cause={}", audio.length, e.toString());
            throw new TranscriptionFailedException("받아쓰기 호출이 실패했습니다", e);
        }

        if (raw == null || raw.isBlank()) {
            throw new TranscriptionFailedException("받아쓰기 응답이 비었습니다");
        }
        JsonNode text;
        try {
            text = objectMapper.readTree(raw).path("text");
        } catch (Exception e) {
            throw new TranscriptionFailedException("받아쓰기 응답을 읽을 수 없습니다", e);
        }
        // 무음이면 whisper 가 빈 문자열을 준다 — 실패가 아니라 "말이 없었다"다
        return text.isMissingNode() || text.isNull() ? "" : text.asText().trim();
    }
}
