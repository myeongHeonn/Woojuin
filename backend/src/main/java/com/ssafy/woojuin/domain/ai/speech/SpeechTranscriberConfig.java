package com.ssafy.woojuin.domain.ai.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 키가 있을 때만 whisper 를 올린다 — 없으면 {@link NoOpSpeechTranscriber}.
 * {@link com.ssafy.woojuin.domain.ai.query.AiQueryConfig} 와 같은 판단 방식이고,
 * 키를 yml 이 아니라 여기서 직접 읽는 이유도 같다(빈 값을 "키 있음"으로 오해하지 않게).
 */
@Slf4j
@Configuration
public class SpeechTranscriberConfig {

    @Bean
    public SpeechTranscriber speechTranscriber(
            ObjectMapper objectMapper,
            @Value("${OPENAI_API_KEY:}") String openAiApiKey,
            @Value("${woojuin.ai.base-url}") String baseUrl,
            @Value("${woojuin.ai.stt-model}") String model,
            @Value("${woojuin.ai.stt-timeout-ms}") long timeoutMs) {

        String apiKey = openAiApiKey == null ? "" : openAiApiKey.trim();
        if (apiKey.isBlank()) {
            log.info("받아쓰기: API 키가 없어 비활성화됩니다(워치 음성 저장이 오류로 응답)");
            return new NoOpSpeechTranscriber();
        }
        log.info("받아쓰기: 활성화 (baseUrl={}, model={})", baseUrl, model);
        WhisperClient client = new WhisperClient(
                baseUrl, apiKey, model, Duration.ofMillis(timeoutMs));
        return new WhisperTranscriber(client, objectMapper);
    }
}
