package com.ssafy.woojuin.domain.integration.dto;

import java.time.OffsetDateTime;

public record LinkCodeResponse(String code, OffsetDateTime expiresAt) {
}
