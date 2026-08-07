package com.ssafy.woojuin.domain.ai.music;

import java.util.Optional;

/**
 * 오디오 한 토막 → 곡 (FR-055). 워치가 손목에서 12초를 녹음해 올린다.
 *
 * <p><b>결과를 세 갈래로 가른다.</b> {@link com.ssafy.woojuin.domain.ai.speech.SpeechTranscriber}
 * 와 같은 이유다 — 화면이 사용자에게 할 말이 각각 다르다.
 *
 * <ul>
 *   <li>{@link Optional#of} — 찾았다. 링크가 반드시 있다(없으면 찾은 것으로 보지 않는다)
 *   <li>{@link Optional#empty} — <b>못 찾았다.</b> 주변이 조용했거나 카탈로그에 없는 곡이다.
 *       오류가 아니므로 "노래를 찾지 못했어요"다
 *   <li>{@link MusicRecognitionFailedException} — 인식 자체가 실패했다(통신·키·제공자 장애).
 *       "잠시 후 다시"다
 * </ul>
 *
 * <p>구현을 인터페이스 뒤에 두는 이유는 제공자를 바꿀 여지를 남기려는 것이다 — 검토 과정과
 * 선택 근거는 {@link MusicRecognizerConfig} javadoc 에 적어 뒀다.
 */
public interface MusicRecognizer {

    /**
     * @param audio 오디오 바이트. 워치는 16kHz 모노 PCM WAV 12초를 보낸다
     * @param filename 확장자로 형식을 알려주는 용도. 예 {@code clip.wav}
     * @return 찾은 곡, 못 찾았으면 빈 값
     * @throws MusicRecognitionFailedException 인식 시도가 실패했다 — 못 찾은 것과 다르다
     */
    Optional<RecognizedSong> recognize(byte[] audio, String filename);

    /**
     * 인식된 곡. <b>{@code link} 는 null 이 아니다</b> — 워치가 이 링크를 URL 아이템으로
     * 저장하므로(장소 저장과 같은 방식) 링크 없는 결과는 애초에 못 찾은 것으로 취급한다.
     *
     * @param title 곡 제목
     * @param artist 아티스트
     * @param link 곡 페이지 링크 — 저장의 재료
     * @param coverUrl 앨범 커버 이미지. 없을 수 있다
     */
    record RecognizedSong(String title, String artist, String link, String coverUrl) {}
}
