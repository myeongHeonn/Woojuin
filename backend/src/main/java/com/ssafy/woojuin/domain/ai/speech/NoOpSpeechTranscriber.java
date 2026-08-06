package com.ssafy.woojuin.domain.ai.speech;

/**
 * {@link SpeechTranscriber} 스텁 — {@code OPENAI_API_KEY} 가 없을 때 올라간다.
 *
 * <p>빈 문자열을 돌려주지 않고 <b>실패로 올린다</b>. 키가 없는 것은 환경 설정 문제인데,
 * 무음으로 뭉개면 워치 화면에 "들린 내용이 없어요"가 떠서 사용자가 마이크를 의심하게 된다 —
 * 그 사이 진짜 원인(키 없음)은 아무 데도 드러나지 않는다.
 */
public class NoOpSpeechTranscriber implements SpeechTranscriber {

    @Override
    public String transcribe(byte[] audio, String filename) {
        throw new TranscriptionFailedException("음성 인식이 설정되지 않았습니다(OPENAI_API_KEY 없음)");
    }
}
