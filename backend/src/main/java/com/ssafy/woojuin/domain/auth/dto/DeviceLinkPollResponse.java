package com.ssafy.woojuin.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 워치 폴링 응답. 승인 전에는 PENDING 만, 승인되면 토큰이 실린다.
 * 만료·미발급 코드는 이 응답이 아니라 400 이다 — 워치는 새 코드를 발급받는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceLinkPollResponse(Status status, String accessToken, String refreshToken) {

    public enum Status { PENDING, APPROVED }

    public static DeviceLinkPollResponse pending() {
        return new DeviceLinkPollResponse(Status.PENDING, null, null);
    }

    public static DeviceLinkPollResponse approved(String accessToken, String refreshToken) {
        return new DeviceLinkPollResponse(Status.APPROVED, accessToken, refreshToken);
    }
}
