package com.ssafy.woojuin.domain.integration.exception;

public class BotAuthenticationException extends RuntimeException {
    public BotAuthenticationException() {
        super("봇 요청 인증에 실패했습니다.");
    }
}
