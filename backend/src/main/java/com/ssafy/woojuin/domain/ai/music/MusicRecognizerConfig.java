package com.ssafy.woojuin.domain.ai.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 있는 경로를 엮어 인식기를 만든다 — 사이드카만, AudD만, 둘 다(폴백), 아무것도 없음(NoOp).
 *
 * <p>둘을 함께 두는 이유: 기본 경로인 shazamio 사이드카는 비공식 API 라 예고 없이 깨진다
 * (같은 라이브러리의 {@code search_track} 은 확인 시점에 이미 깨져 있었다). 시연 중에
 * 깨져도 기능이 죽지 않게 정식 API(AudD)를 뒤에 둔다. 대신 AudD 는 무료 300건 뒤 자동
 * 과금이라 <b>기본 경로가 실패했을 때만</b> 불린다({@link FallbackMusicRecognizer}).
 *
 * <p>{@code AUDD_API_TOKEN} 이 없으면 사이드카 단독으로 돈다 — 키 없이도 기능이 동작하는
 * 것이 기본값이다({@link com.ssafy.woojuin.domain.ai.speech.SpeechTranscriberConfig} 와
 * 같은 태도: 설정이 없으면 조용히 기능만 꺼지고 앱은 뜬다).
 *
 * <p><b>배포 환경에서는 순서를 뒤집을 수 있다</b>({@code AI_MUSIC_PRIMARY=audd}). 로컬에서
 * 되는 것이 EC2 에서 된다는 보장이 없기 때문이다 — 비공식 경로는 데이터센터 IP 를 먼저
 * 막는 편이고, 막는 방식이 "빠른 거부"가 아니라 "응답 없음"이면 폴백까지 타임아웃(수십 초)을
 * 다 기다린다. 워치에서 그 시간은 실패와 같으므로, 막힌 환경에서는 정식 API 를 앞에 둔다.
 */
@Slf4j
@Configuration
public class MusicRecognizerConfig {

    @Bean
    public MusicRecognizer musicRecognizer(
            ObjectMapper objectMapper,
            @Value("${woojuin.music.sidecar-base-url:}") String sidecarBaseUrl,
            @Value("${woojuin.music.timeout-ms}") long timeoutMs,
            @Value("${woojuin.music.primary}") String primaryName,
            @Value("${AUDD_API_TOKEN:}") String auddToken) {

        Duration timeout = Duration.ofMillis(timeoutMs);
        MusicRecognizer sidecar = null;
        if (sidecarBaseUrl != null && !sidecarBaseUrl.isBlank()) {
            sidecar = new SidecarMusicRecognizer(
                    new SidecarMusicClient(sidecarBaseUrl.trim(), timeout), objectMapper);
        }
        MusicRecognizer audd = null;
        String token = auddToken == null ? "" : auddToken.trim();
        if (!token.isBlank()) {
            audd = new AuddMusicRecognizer(token, timeout, objectMapper);
        }

        if (sidecar != null && audd != null) {
            boolean auddFirst = "audd".equalsIgnoreCase(primaryName == null ? "" : primaryName.trim());
            if (auddFirst) {
                log.info("노래 인식: AudD 우선 + 사이드카({}) 폴백", sidecarBaseUrl);
                return new FallbackMusicRecognizer(audd, sidecar);
            }
            log.info("노래 인식: 사이드카({}) 우선 + AudD 폴백", sidecarBaseUrl);
            return new FallbackMusicRecognizer(sidecar, audd);
        }
        if (sidecar != null) {
            log.info("노래 인식: 사이드카({}) 단독 — AUDD_API_TOKEN 이 없어 폴백 없음", sidecarBaseUrl);
            return sidecar;
        }
        if (audd != null) {
            log.info("노래 인식: AudD 단독 — 사이드카 주소가 비어 있음");
            return audd;
        }
        log.info("노래 인식: 경로가 없어 비활성화됩니다(워치 노래 찾기가 오류로 응답)");
        return new NoOpMusicRecognizer();
    }
}
