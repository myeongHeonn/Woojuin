package com.ssafy.woojuin.domain.integration.dto;

public record ChatCommandResult(String message) {

    public static ChatCommandResult of(String message) {
        return new ChatCommandResult(message);
    }
}
