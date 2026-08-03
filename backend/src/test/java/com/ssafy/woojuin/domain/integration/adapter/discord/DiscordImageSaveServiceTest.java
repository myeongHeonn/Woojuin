package com.ssafy.woojuin.domain.integration.adapter.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.integration.entity.ChatAccountConnection;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.repository.ChatAccountConnectionRepository;
import com.ssafy.woojuin.domain.integration.service.ChatCommandDeduplicationService;
import com.ssafy.woojuin.domain.item.service.ItemService;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscordImageSaveServiceTest {

    @Mock private ChatAccountConnectionRepository connectionRepository;
    @Mock private ChatCommandDeduplicationService deduplicationService;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private ItemService itemService;
    @Mock private User user;
    @Mock private Workspace workspace;

    private DiscordImageSaveService service;

    @BeforeEach
    void setUp() {
        service = new DiscordImageSaveService(
                connectionRepository, deduplicationService, workspaceRepository, itemService);
        ChatAccountConnection connection = new ChatAccountConnection(ChatPlatform.DISCORD, "discord-user", user);
        connection.changeDefaultWorkspace(workspace);
        when(connectionRepository.findByPlatformAndExternalUserId(ChatPlatform.DISCORD, "discord-user"))
                .thenReturn(Optional.of(connection));
        when(deduplicationService.acquire(ChatPlatform.DISCORD, "request-1")).thenReturn(true);
        when(workspace.getId()).thenReturn(7L);
    }

    @Test
    void rejectsNonDiscordCdnUrlBeforeDownload() {
        DiscordAttachment attachment = new DiscordAttachment(
                "file-1", "photo.png", "image/png", 100,
                "https://example.com/attachments/1/2/photo.png");

        assertThatThrownBy(() -> service.save(
                "discord-user", "request-1", null, List.of(attachment)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Discord 첨부파일 주소가 올바르지 않아요.");

        verify(itemService, never()).createFromImage(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(deduplicationService).release(ChatPlatform.DISCORD, "request-1");
    }

    @Test
    void rejectsOversizedImageBeforeDownload() {
        DiscordAttachment attachment = new DiscordAttachment(
                "file-1", "photo.png", "image/png", DiscordImageSaveService.MAX_IMAGE_BYTES + 1,
                "https://cdn.discordapp.com/attachments/1/2/photo.png");

        assertThatThrownBy(() -> service.save(
                "discord-user", "request-1", null, List.of(attachment)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미지는 비어 있지 않은 10MB 이하 파일만 저장할 수 있어요.");
    }

    @Test
    void ignoresUnsupportedAttachments() {
        DiscordAttachment attachment = new DiscordAttachment(
                "file-1", "document.pdf", "application/pdf", 100,
                "https://cdn.discordapp.com/attachments/1/2/document.pdf");

        var result = service.save("discord-user", "request-1", null, List.of(attachment));

        assertThat(result.message()).contains("지원하는 이미지가 없어요");
        verify(itemService, never()).createFromImage(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
