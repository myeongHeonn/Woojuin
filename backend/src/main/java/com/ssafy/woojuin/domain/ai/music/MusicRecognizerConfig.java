package com.ssafy.woojuin.domain.ai.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 키가 있으면 AudD 를, 없으면 {@link NoOpMusicRecognizer} 를 올린다
 * ({@link com.ssafy.woojuin.domain.ai.speech.SpeechTranscriberConfig} 와 같은 태도: 설정이
 * 없으면 조용히 그 기능만 꺼지고 앱은 뜬다).
 *
 * <p><b>무료 경로(shazamio)를 검토했지만 넣지 않았다.</b> 라이브로 확인해 보니 동작은 했다
 * (12초 WAV 를 601ms 에 인식, 제목·아티스트·앨범 커버까지). 그런데 그것은 Shazam 내부 API 를
 * 리버스 엔지니어링해 <b>Apple 의 인프라를 계약 없이 쓰는 것</b>이고, 세 가지가 걸렸다.
 *
 * <ul>
 *   <li>배포 환경에서 막힐 가능성이 크다 — 비공식 경로는 데이터센터(EC2) IP 를 먼저 차단하는
 *       편이고, 우리 검증은 전부 가정용 IP 에서 나갔다. 막히는 방식이 "응답 없음"이면
 *       타임아웃까지 기다린 뒤 폴백으로 넘어가 워치에서 체감이 실패와 같다
 *   <li>약관 위반 소지가 있어 상용 출시에 쓸 수 없다 — 지금 붙였다가 나중에 걷는 것보다
 *       처음부터 정식 경로만 두는 쪽이 낫다
 *   <li>사이드카 컨테이너 하나와 파이프라인 세 곳(빌드·기동·스모크)이 늘어난다. 배포에서
 *       못 쓸 경로를 위해 지불할 비용이 아니다
 * </ul>
 *
 * <p>그래서 정식 API 하나로 간다. 인식기가 인터페이스 뒤에 있으므로 제공자를 바꾸거나 다시
 * 늘리는 일은 이 설정과 구현 한 클래스로 끝난다.
 */
@Slf4j
@Configuration
public class MusicRecognizerConfig {

    @Bean
    public MusicRecognizer musicRecognizer(
            ObjectMapper objectMapper,
            @Value("${woojuin.music.timeout-ms}") long timeoutMs,
            @Value("${AUDD_API_TOKEN:}") String auddToken) {

        String token = auddToken == null ? "" : auddToken.trim();
        if (token.isBlank()) {
            log.info("노래 인식: AUDD_API_TOKEN 이 없어 비활성화됩니다(워치 노래 찾기가 오류로 응답)");
            return new NoOpMusicRecognizer();
        }
        log.info("노래 인식: AudD 활성화 (timeoutMs={})", timeoutMs);
        return new AuddMusicRecognizer(token, Duration.ofMillis(timeoutMs), objectMapper);
    }
}
