package com.ssafy.woojuin.domain.ai.music;

import java.util.Optional;

/**
 * {@link MusicRecognizer} 스텁 — {@code AUDD_API_TOKEN} 이 없을 때 올라간다.
 *
 * <p>빈 결과가 아니라 <b>실패로 올린다</b>. 빈 결과는 "이 곡을 못 찾았다"는 뜻이어서,
 * 설정이 빠진 것을 그렇게 답하면 사용자가 노래를 계속 들려주며 기다린다 — 그 사이
 * 진짜 원인(설정 없음)은 아무 데도 드러나지 않는다.
 */
public class NoOpMusicRecognizer implements MusicRecognizer {

    @Override
    public Optional<RecognizedSong> recognize(byte[] audio, String filename) {
        throw new MusicRecognitionFailedException("노래 인식이 설정되지 않았습니다");
    }
}
