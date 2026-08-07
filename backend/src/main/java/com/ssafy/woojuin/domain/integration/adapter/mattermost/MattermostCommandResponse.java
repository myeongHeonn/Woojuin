package com.ssafy.woojuin.domain.integration.adapter.mattermost;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Mattermost가 요구하는 최상위 응답 형식. 플랫폼 콜백이라 일반 ApiResponse를 감싸지 않는다. */
public record MattermostCommandResponse(
        @JsonProperty("response_type") String responseType,
        String text) {

    public static MattermostCommandResponse ephemeral(String text) {
        return new MattermostCommandResponse("ephemeral", text);
    }
}
