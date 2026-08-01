package com.ssafy.woojuin.domain.ai.usage;

public class AiUsageUnavailableException extends RuntimeException {

    public AiUsageUnavailableException(String message) {
        super(message);
    }

    public AiUsageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
