package com.ssafy.woojuin.domain.integration.adapter.discord;

public record DiscordInteractionResponse(int type, Data data) {

    private static final int PONG = 1;
    private static final int CHANNEL_MESSAGE_WITH_SOURCE = 4;
    private static final int DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE = 5;
    private static final int EPHEMERAL = 1 << 6;
    private static final int SUPPRESS_EMBEDS = 1 << 2;

    public static DiscordInteractionResponse pong() {
        return new DiscordInteractionResponse(PONG, null);
    }

    public static DiscordInteractionResponse ephemeral(String content) {
        // SUPPRESS_EMBEDS: 응답 안의 링크(딥링크·원문)를 Discord가 카드로 자동 펼치지 않도록 한다.
        return new DiscordInteractionResponse(
                CHANNEL_MESSAGE_WITH_SOURCE,
                new Data(content, EPHEMERAL | SUPPRESS_EMBEDS));
    }

    public static DiscordInteractionResponse deferredEphemeral() {
        return new DiscordInteractionResponse(
                DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE,
                new Data(null, EPHEMERAL));
    }

    public record Data(String content, int flags) {
    }
}
