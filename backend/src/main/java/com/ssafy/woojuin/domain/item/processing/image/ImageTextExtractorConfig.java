package com.ssafy.woojuin.domain.item.processing.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * OPENROUTER_API_KEY가 설정돼 있으면 Qwen3-VL 추출기를, 없으면 NoOp 스텁을 올린다.
 * 키 없는 로컬 환경에서도 앱이 뜨고 이미지 저장·썸네일·EXIF 좌표는 그대로 동작하게
 * 하려는 것이다(AiQueryConfig의 키 분기와 동일 패턴). 키를 yml이 아니라 여기서 직접
 * 읽는 이유도 동일 — {@code ${VAR:기본값}}은 빈 줄(`OPENROUTER_API_KEY=`)을 "키 있음"으로
 * 오해한다.
 *
 * <p>프롬프트는 묶음 F가 50장 회귀 테스트로 확정한 것(ai-test 브랜치
 * {@code ai/ai-image/prompts/analyze_image_fast.txt})의 사본이다. 수정할 일이 생기면
 * F의 평가 절차를 다시 태울 것.
 */
@Slf4j
@Configuration
public class ImageTextExtractorConfig {

    @Bean
    public ImageTextExtractor imageTextExtractor(
            ObjectMapper objectMapper,
            @Value("${OPENROUTER_API_KEY:}") String openRouterApiKey,
            @Value("${woojuin.vision.base-url}") String baseUrl,
            @Value("${woojuin.vision.model}") String model,
            @Value("${woojuin.vision.timeout-ms}") long timeoutMs,
            @Value("classpath:prompts/analyze-image.txt") Resource promptResource)
            throws Exception {

        String apiKey = openRouterApiKey == null ? "" : openRouterApiKey.trim();

        if (apiKey.isBlank()) {
            log.info("ImageTextExtractor: API 키가 없어 NoOp으로 동작합니다 — 이미지 텍스트 추출을 "
                    + "건너뜁니다(저장·썸네일·EXIF 좌표는 정상). .env에 OPENROUTER_API_KEY를 넣으면 활성화됩니다");
            return new NoOpImageTextExtractor();
        }

        String prompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        log.info("ImageTextExtractor: Qwen3-VL 활성화 (baseUrl={}, model={})", baseUrl, model);
        return new QwenVisionImageTextExtractor(
                baseUrl, apiKey, model, Duration.ofMillis(timeoutMs), prompt, objectMapper);
    }
}
