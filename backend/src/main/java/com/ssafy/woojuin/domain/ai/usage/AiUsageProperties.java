package com.ssafy.woojuin.domain.ai.usage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "woojuin.ai-usage")
public record AiUsageProperties(
        boolean enabled,
        int monthlyItemLimit,
        int reservationTtlMinutes) {

    public AiUsageProperties {
        if (monthlyItemLimit <= 0) {
            throw new IllegalArgumentException("AI 월간 아이템 제한은 1 이상이어야 합니다");
        }
        if (reservationTtlMinutes <= 0) {
            throw new IllegalArgumentException("AI 사용량 예약 TTL은 1분 이상이어야 합니다");
        }
    }
}
