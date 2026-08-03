package com.ssafy.woojuin.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.integration.dto.BotItemCreateRequest;
import com.ssafy.woojuin.domain.integration.entity.ChatChannelMapping;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.exception.BotAuthenticationException;
import com.ssafy.woojuin.domain.integration.repository.ChatChannelMappingRepository;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.service.ItemService;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BotItemServiceTest {
    private final ChatChannelMappingRepository repository = mock(ChatChannelMappingRepository.class);
    private final ChatCommandDeduplicationService deduplication = mock(ChatCommandDeduplicationService.class);
    private final ItemService itemService = mock(ItemService.class);
    private final BotItemService service = new BotItemService(repository, deduplication, itemService, "secret");

    @Test
    void rejectsInvalidSecretBeforeLookup() {
        assertThatThrownBy(() -> service.create("wrong", ChatPlatform.DISCORD, "request-1",
                new BotItemCreateRequest("channel", ItemType.URL, "https://example.com", null)))
                .isInstanceOf(BotAuthenticationException.class);
        verifyNoInteractions(repository, itemService);
    }

    @Test
    void savesThroughExistingItemServiceUsingMappingCreator() {
        Workspace workspace = mock(Workspace.class);
        User creator = mock(User.class);
        when(workspace.getId()).thenReturn(7L);
        when(creator.getId()).thenReturn(3L);
        ChatChannelMapping mapping = new ChatChannelMapping(ChatPlatform.DISCORD, "channel", workspace, creator);
        when(deduplication.acquire(ChatPlatform.DISCORD, "request-1")).thenReturn(true);
        when(repository.findByPlatformAndChannelId(ChatPlatform.DISCORD, "channel"))
                .thenReturn(Optional.of(mapping));
        when(itemService.createFromRequest(eq(7L), eq(3L), any())).thenReturn(mock(ItemCreateResponse.class));

        service.create("secret", ChatPlatform.DISCORD, "request-1",
                new BotItemCreateRequest("channel", ItemType.URL, "https://example.com", null));

        verify(itemService).createFromRequest(eq(7L), eq(3L), argThat(request ->
                request.type() == ItemType.URL && "https://example.com".equals(request.url())));
    }

    @Test
    void duplicateRequestDoesNotSaveAgain() {
        when(deduplication.acquire(ChatPlatform.MATTERMOST, "retry-id")).thenReturn(false);
        assertThatThrownBy(() -> service.create("secret", ChatPlatform.MATTERMOST, "retry-id",
                new BotItemCreateRequest("channel", ItemType.MEMO, null, "memo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 처리된");
        verifyNoInteractions(repository, itemService);
    }
}
