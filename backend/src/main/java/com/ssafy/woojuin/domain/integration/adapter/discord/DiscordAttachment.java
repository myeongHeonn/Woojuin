package com.ssafy.woojuin.domain.integration.adapter.discord;

public record DiscordAttachment(
        String id,
        String filename,
        String contentType,
        long size,
        String url) {
}
