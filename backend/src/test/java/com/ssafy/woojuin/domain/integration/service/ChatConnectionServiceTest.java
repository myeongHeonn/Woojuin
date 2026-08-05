package com.ssafy.woojuin.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.ssafy.woojuin.domain.integration.dto.ChatConnectionResponse;
import com.ssafy.woojuin.domain.integration.entity.ChatAccountConnection;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.repository.ChatAccountConnectionRepository;
import com.ssafy.woojuin.domain.integration.repository.ChatChannelMappingRepository;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatConnectionServiceTest {

    private final ChatAccountConnectionRepository connections = mock(ChatAccountConnectionRepository.class);
    private final ChatChannelMappingRepository mappings = mock(ChatChannelMappingRepository.class);
    private final ChatConnectionService service = new ChatConnectionService(connections, mappings);

    @Test
    void listsConnectionsWithDefaultWorkspace() {
        ChatAccountConnection connection = mock(ChatAccountConnection.class);
        Workspace workspace = mock(Workspace.class);
        when(connection.getId()).thenReturn(5L);
        when(connection.getPlatform()).thenReturn(ChatPlatform.DISCORD);
        when(connection.getDefaultWorkspace()).thenReturn(workspace);
        when(workspace.getId()).thenReturn(9L);
        when(workspace.getName()).thenReturn("몽골 여행");
        when(connections.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(connection));

        List<ChatConnectionResponse> result = service.list(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(5L);
        assertThat(result.get(0).platform()).isEqualTo(ChatPlatform.DISCORD);
        assertThat(result.get(0).defaultWorkspaceId()).isEqualTo(9L);
        assertThat(result.get(0).defaultWorkspaceName()).isEqualTo("몽골 여행");
    }

    @Test
    void listsConnectionWithoutDefaultWorkspaceAsNull() {
        // 링크 코드로 막 연결한 직후에는 기본 워크스페이스가 없을 수 있다 — 화면이 이 null 을
        // "기본 워크스페이스 미지정"으로 그린다
        ChatAccountConnection connection = mock(ChatAccountConnection.class);
        when(connection.getPlatform()).thenReturn(ChatPlatform.MATTERMOST);
        when(connection.getDefaultWorkspace()).thenReturn(null);
        when(connections.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(connection));

        List<ChatConnectionResponse> result = service.list(1L);

        assertThat(result.get(0).defaultWorkspaceId()).isNull();
        assertThat(result.get(0).defaultWorkspaceName()).isNull();
    }

    @Test
    void rejectsDisconnectOfSomeoneElsesConnection() {
        // 본인 소유 확인이 조회 조건에 들어 있어, 남의 id 는 "없는 연동"과 같은 결과다 —
        // 존재 여부도 새어 나가지 않는다
        when(connections.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disconnect(1L, 5L))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mappings);
        verify(connections, never()).delete(any());
    }

    @Test
    void disconnectDeletesConnectionAndOwnMappingsOfThatPlatform() {
        ChatAccountConnection connection = mock(ChatAccountConnection.class);
        when(connection.getPlatform()).thenReturn(ChatPlatform.DISCORD);
        when(connections.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(connection));

        service.disconnect(1L, 5L);

        // 행을 지운다(하드 삭제) — (platform, externalUserId) 유니크 제약 때문에 행이 남으면
        // 같은 계정으로 다시 연결할 수 없고, OAuth 토큰도 행과 함께 사라진다
        verify(connections).delete(connection);
        // 봇은 매핑 생성자 명의로 저장하므로(BotItemService), 매핑을 남겨 두면 연동을 끊은
        // 사람 명의로 그 채널이 계속 저장한다
        verify(mappings).deleteByPlatformAndCreatedById(ChatPlatform.DISCORD, 1L);
    }
}
