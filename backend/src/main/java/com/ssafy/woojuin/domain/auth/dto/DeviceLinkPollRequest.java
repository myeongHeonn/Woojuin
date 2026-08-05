package com.ssafy.woojuin.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceLinkPollRequest(
        @NotBlank(message = "코드는 필수입니다") String code) {
}
