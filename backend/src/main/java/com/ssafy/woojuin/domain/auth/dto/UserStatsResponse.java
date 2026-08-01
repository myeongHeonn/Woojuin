package com.ssafy.woojuin.domain.auth.dto;

public record UserStatsResponse(
        long totalSaved,
        long workspaceCount,
        long savedThisWeek) {
}
