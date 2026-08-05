package com.ssafy.woojuin.domain.auth.dto;

/** 워치 화면에 띄울 링크 코드와 유효 시간 — 만료되면 워치가 새로 발급받는다 */
public record DeviceLinkStartResponse(String code, long expiresInSeconds) {
}
