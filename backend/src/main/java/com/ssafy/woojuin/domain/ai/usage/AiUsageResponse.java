package com.ssafy.woojuin.domain.ai.usage;

import java.time.OffsetDateTime;

public record AiUsageResponse(
        String period,
        long used,
        int limit,
        Long remaining,
        boolean unlimited,
        boolean limitEnabled,
        OffsetDateTime resetAt) {
}
