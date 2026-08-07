package com.ssafy.woojuin.domain.ai.speech;

/**
 * 음성(오디오) → 문장. 워치가 손목에서 녹음한 소리를 보내면 여기서 글자로 바꾼다.
 *
 * <p><b>왜 서버가 하는가.</b> Wear OS 워치에는 구글 검색 앱이 없어 온디바이스 SODA 엔진만
 * 붙고, 그 엔진은 짧은 발화용(AMBIENT_ONESHOT)이라 고유명사가 자주 깨진다. 실기기에서 같은
 * 문장("성수동 파스타집 온화정 다음 주에 가보기")을 두 번 말했을 때 각각
 * "선수동 파스타집 운화점 가옥기", "성수동 바닷가 다음 주에가 보기"로 저장됐다. 같은 오디오를
 * whisper 로 보내면 정확히 받아쓴다. 저장물의 제목이 될 문장이라 정확도가 곧 기능이다.
 *
 * <p><b>다른 AI 계약과 다르게 실패를 감춘다.</b> {@link com.ssafy.woojuin.domain.ai.AiAnalyzer}
 * 나 {@link com.ssafy.woojuin.domain.location.Geocoder} 는 실패를 빈 값으로 흡수하지만
 * ("없으면 없는 대로 저장"), 여기서 빈 문자열은 <b>"사용자가 아무 말도 하지 않았다"</b>는
 * 뜻이어야 한다. 호출 실패를 무음으로 뭉개면 워치가 "들린 내용이 없어요"를 보여주고 사용자는
 * 다시 말하지만 역시 실패한다 — 그래서 실패는 예외로 올린다.
 */
public interface SpeechTranscriber {

    /**
     * @param audio 오디오 바이트. 워치는 16kHz 모노 PCM WAV 를 보낸다
     * @param filename 확장자로 형식을 알려주는 용도(whisper 가 본다). 예 {@code speech.wav}
     * @return 받아쓴 문장. <b>무음이면 빈 문자열</b>
     * @throws TranscriptionFailedException 호출·인증·파싱 실패. 무음과 구분된다
     */
    String transcribe(byte[] audio, String filename);
}
