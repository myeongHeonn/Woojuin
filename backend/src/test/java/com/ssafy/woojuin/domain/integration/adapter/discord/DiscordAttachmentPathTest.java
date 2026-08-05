package com.ssafy.woojuin.domain.integration.adapter.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordAttachmentPathTest {

    @Test
    void acceptsOfficialDiscordAttachmentPathsOnly() {
        assertThat(DiscordImageSaveService.isDiscordAttachmentPath(
                "/ephemeral-attachments/1/2/photo.png")).isTrue();
        assertThat(DiscordImageSaveService.isDiscordAttachmentPath(
                "/attachments/1/2/photo.png")).isTrue();
        assertThat(DiscordImageSaveService.isDiscordAttachmentPath(
                "/untrusted/1/2/photo.png")).isFalse();
    }
}
