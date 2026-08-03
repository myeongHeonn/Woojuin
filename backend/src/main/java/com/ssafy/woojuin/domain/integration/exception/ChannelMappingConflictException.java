package com.ssafy.woojuin.domain.integration.exception;

public class ChannelMappingConflictException extends RuntimeException {
    public ChannelMappingConflictException() {
        super("COMMON_409: 이미 매핑된 채널입니다.");
    }
}
