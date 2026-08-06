package com.ssafy.woojuin.domain.ai.music;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * 기본 인식기가 <b>실패했을 때만</b> 예비 인식기로 한 번 더 시도한다.
 *
 * <p>왜 필요한가: 기본 경로(shazamio 사이드카)는 Shazam 내부 API 를 리버스 엔지니어링한
 * 비공식 라이브러리다. 실제로 같은 라이브러리의 다른 엔드포인트({@code search_track})는
 * 확인 시점에 이미 깨져 있었다. 시연 중에 깨져도 기능이 죽지 않게 정식 API 를 뒤에 둔다.
 *
 * <p><b>"못 찾음"에는 폴백하지 않는다.</b> 그건 정상적인 답이고, 예비 경로는 대개 유료라
 * (AudD 는 무료 300건 뒤 초과분이 자동 과금된다) 조용히 두 배로 쓰면 안 된다. 조용한 방에서
 * 녹음한 무음이 매번 두 곳에 올라가는 것도 원치 않는다.
 */
@Slf4j
public class FallbackMusicRecognizer implements MusicRecognizer {

    private final MusicRecognizer primary;
    private final MusicRecognizer secondary;

    public FallbackMusicRecognizer(MusicRecognizer primary, MusicRecognizer secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public Optional<RecognizedSong> recognize(byte[] audio, String filename) {
        try {
            return primary.recognize(audio, filename);
        } catch (MusicRecognitionFailedException e) {
            log.info("기본 노래 인식 실패 — 예비 경로로 재시도: {}", e.getMessage());
        }
        return secondary.recognize(audio, filename);
    }
}
