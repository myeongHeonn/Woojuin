package com.ssafy.woojuin.domain.ai.speech;

/**
 * 받아쓰기가 실패했다 — <b>사용자가 말을 안 한 것과는 다르다</b>(무음은 빈 문자열이다).
 * {@link com.ssafy.woojuin.global.error.GlobalExceptionHandler} 가 503 으로 내보내고,
 * 워치는 "잠시 후 다시" 계열의 오류를 보여준다.
 */
public class TranscriptionFailedException extends RuntimeException {

    public TranscriptionFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public TranscriptionFailedException(String message) {
        super(message);
    }
}
