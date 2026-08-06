package com.ssafy.woojuin.domain.ai.music;

/**
 * 인식 시도가 실패했다 — <b>곡을 못 찾은 것과는 다르다</b>(그건 빈 결과다).
 * {@link com.ssafy.woojuin.global.error.GlobalExceptionHandler} 가 503 으로 내보내고,
 * 워치는 "잠시 후 다시" 계열을 보여준다.
 */
public class MusicRecognitionFailedException extends RuntimeException {

    public MusicRecognitionFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public MusicRecognitionFailedException(String message) {
        super(message);
    }
}
