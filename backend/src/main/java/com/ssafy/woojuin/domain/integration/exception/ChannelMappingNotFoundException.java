package com.ssafy.woojuin.domain.integration.exception;

public class ChannelMappingNotFoundException extends RuntimeException {
    public ChannelMappingNotFoundException() {
        super("등록된 채널 매핑을 찾을 수 없습니다.");
    }
}
