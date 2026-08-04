package com.ssafy.woojuin.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.ai.usage.AiUsageLimitExceededException;
import com.ssafy.woojuin.domain.auth.entity.AuthProvider;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.integration.dto.ChatCommand;
import com.ssafy.woojuin.domain.integration.dto.ChatCommandResult;
import com.ssafy.woojuin.domain.integration.entity.ChatAccountConnection;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.repository.ChatAccountConnectionRepository;
import com.ssafy.woojuin.domain.integration.service.ChatCommandService.ChatAccountNotLinkedException;
import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSearchResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSummaryResponse;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.service.ItemSearchService;
import com.ssafy.woojuin.domain.item.service.ItemService;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceType;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatCommandServiceTest {

    @Mock ChatAccountConnectionRepository connectionRepository;
    @Mock ChatLinkCodeService linkCodeService;
    @Mock ChatCommandDeduplicationService deduplicationService;
    @Mock UserRepository userRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock WorkspaceMemberRepository workspaceMemberRepository;
    @Mock ItemService itemService;
    @Mock ItemSearchService itemSearchService;
    @Mock ItemRepository itemRepository;

    private ChatCommandService service;
    private ChatAccountConnection connection;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        service = new ChatCommandService(connectionRepository, linkCodeService, deduplicationService,
                userRepository, workspaceRepository, workspaceMemberRepository, itemService, itemSearchService,
                itemRepository, "http://localhost:5173");
        User user = User.builder()
                .email("user@example.com")
                .passwordHash("hash")
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .nickname("user")
                .build();
        ReflectionTestUtils.setField(user, "id", 7L);
        workspace = Workspace.builder().name("마이스페이스").type(WorkspaceType.PERSONAL).createdBy(user).build();
        ReflectionTestUtils.setField(workspace, "id", 11L);
        connection = new ChatAccountConnection(ChatPlatform.MATTERMOST, "mm-user", user);
        connection.changeDefaultWorkspace(workspace);
    }

    @Test
    void savesValidUrlThroughExistingItemService() {
        arrangeConnected("request-1");
        when(itemService.createFromRequest(eq(11L), eq(7L), any(ItemCreateRequest.class)))
                .thenReturn(new ItemCreateResponse(42L, ItemStatus.PROCESSING, OffsetDateTime.now()));
        when(workspaceRepository.findById(11L)).thenReturn(Optional.of(workspace));

        ChatCommandResult result = service.handle(command("request-1", "save https://example.com"));

        assertThat(result.message())
                .contains("우주인으로 보냈어요")
                .contains("저장 공간: 마이스페이스")
                .contains("종류: 링크")
                .contains("[우주인에서 열기](http://localhost:5173/workspace/11/library?item=42)")
                .doesNotContain("PROCESSING");
        verify(itemService).createFromRequest(eq(11L), eq(7L),
                eq(new ItemCreateRequest(com.ssafy.woojuin.domain.item.entity.ItemType.URL,
                        "https://example.com", null)));
    }

    @Test
    void rejectsInvalidUrlBeforeCallingItemService() {
        arrangeConnected("request-2");

        ChatCommandResult result = service.handle(command("request-2", "save not-a-url"));

        assertThat(result.message()).contains("올바른 http 또는 https URL");
        verify(itemService, never()).createFromRequest(any(), any(), any());
    }

    @Test
    void reportsUnlinkedAccount() {
        when(deduplicationService.acquire(ChatPlatform.MATTERMOST, "request-3")).thenReturn(true);
        when(connectionRepository.findByPlatformAndExternalUserId(ChatPlatform.MATTERMOST, "mm-user"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(command("request-3", "save https://example.com")))
                .isInstanceOf(ChatAccountNotLinkedException.class);
        verify(itemService, never()).createFromRequest(any(), any(), any());
    }

    @Test
    void reportsWorkspaceAccessDenied() {
        arrangeConnected("request-4");
        when(itemService.createFromRequest(eq(99L), eq(7L), any()))
                .thenThrow(new WorkspaceAccessDeniedException(99L));

        ChatCommandResult result = service.handle(
                command("request-4", "save https://example.com --workspace 99"));

        assertThat(result.message()).contains("접근할 권한이 없어요");
    }

    @Test
    void reportsMonthlyLimitExceeded() {
        arrangeConnected("request-5");
        when(itemService.createFromRequest(eq(11L), eq(7L), any()))
                .thenThrow(new AiUsageLimitExceededException(50));

        ChatCommandResult result = service.handle(command("request-5", "save https://example.com"));

        assertThat(result.message()).contains("이번 달 저장 횟수");
    }

    @Test
    void suppressesPlatformRetry() {
        when(deduplicationService.acquire(ChatPlatform.MATTERMOST, "same-trigger")).thenReturn(false);

        ChatCommandResult result = service.handle(command("same-trigger", "save https://example.com"));

        assertThat(result.message()).contains("이미 처리한 요청");
        verify(connectionRepository, never()).findByPlatformAndExternalUserId(any(), any());
        verify(itemService, never()).createFromRequest(any(), any(), any());
    }

    @Test
    void savesMemoThroughExistingItemService() {
        arrangeConnected("memo-request");
        when(itemService.createFromRequest(eq(11L), eq(7L), any(ItemCreateRequest.class)))
                .thenReturn(new ItemCreateResponse(43L, ItemStatus.PROCESSING, OffsetDateTime.now()));
        when(workspaceRepository.findById(11L)).thenReturn(Optional.of(workspace));

        ChatCommandResult result = service.handle(command("memo-request", "memo 다음 회의 일정 확인"));

        assertThat(result.message())
                .contains("우주인으로 보냈어요")
                .contains("저장 공간: 마이스페이스")
                .contains("종류: 메모")
                .contains("[우주인에서 열기](http://localhost:5173/workspace/11/library?item=43)");
        verify(itemService).createFromRequest(eq(11L), eq(7L),
                eq(new ItemCreateRequest(
                        com.ssafy.woojuin.domain.item.entity.ItemType.MEMO,
                        null,
                        "다음 회의 일정 확인")));
    }

    @Test
    void showsConnectedAccountAndDefaultWorkspace() {
        arrangeConnected("account-request");

        ChatCommandResult result = service.handle(command("account-request", "account"));

        assertThat(result.message()).contains("user@example.com").contains(workspace.getName());
    }

    @Test
    void disconnectsConnectedAccount() {
        arrangeConnected("disconnect-request");

        ChatCommandResult result = service.handle(command("disconnect-request", "disconnect"));

        assertThat(result.message()).contains("연동을 해제했어요");
        verify(connectionRepository).delete(connection);
    }

    @Test
    void searchShowsOriginalMemoAndWoojuinDeepLink() {
        arrangeConnected("search-request");
        ItemSummaryResponse summary = new ItemSummaryResponse(
                51L, ItemType.MEMO, ItemStatus.DONE, "AI가 만든 제목", null, "AI 요약",
                null, null, List.of(), false, OffsetDateTime.now(), null);
        when(itemSearchService.search(11L, 7L, "야구", 0, 5))
                .thenReturn(new ItemSearchResponse(List.of(summary), 0, 5, 1, false, false, 0));
        Item memo = Item.builder().workspaceId(11L).createdBy(7L).type(ItemType.MEMO)
                .content("다음 주 야구 경기 티켓 예매하기").build();
        ReflectionTestUtils.setField(memo, "id", 51L);
        when(itemRepository.findAllById(any())).thenReturn(List.of(memo));

        ChatCommandResult result = service.handle(command("search-request", "search 야구"));

        assertThat(result.message())
                .contains("다음 주 야구 경기 티켓 예매하기")
                .contains("http://localhost:5173/workspace/11/library?item=51")
                .doesNotContain("AI가 만든 제목");
    }

    private void arrangeConnected(String requestId) {
        when(deduplicationService.acquire(ChatPlatform.MATTERMOST, requestId)).thenReturn(true);
        when(connectionRepository.findByPlatformAndExternalUserId(ChatPlatform.MATTERMOST, "mm-user"))
                .thenReturn(Optional.of(connection));
    }

    private ChatCommand command(String requestId, String text) {
        return new ChatCommand(ChatPlatform.MATTERMOST, "mm-user", requestId, text);
    }
}
