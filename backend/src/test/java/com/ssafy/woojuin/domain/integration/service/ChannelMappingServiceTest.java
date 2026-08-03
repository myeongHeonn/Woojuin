package com.ssafy.woojuin.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.integration.dto.ChannelMappingRequest;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.repository.ChatChannelMappingRepository;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceMemberRequiredException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChannelMappingServiceTest {
    private final ChatChannelMappingRepository repository = mock(ChatChannelMappingRepository.class);
    private final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ChannelMappingService service = new ChannelMappingService(repository, members, users);

    @Test
    void requiresWorkspaceMembershipBeforeCreatingMapping() {
        when(members.findByWorkspaceIdAndUserId(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L,
                new ChannelMappingRequest(ChatPlatform.DISCORD, "channel", 9L)))
                .isInstanceOf(WorkspaceMemberRequiredException.class);

        verifyNoInteractions(users, repository);
    }

    @Test
    void createsMappingForWorkspaceMember() {
        WorkspaceMember member = mock(WorkspaceMember.class);
        Workspace workspace = mock(Workspace.class);
        User user = mock(User.class);
        when(members.findByWorkspaceIdAndUserId(9L, 1L)).thenReturn(Optional.of(member));
        when(member.getWorkspace()).thenReturn(workspace);
        when(workspace.getId()).thenReturn(9L);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(repository.findByPlatformAndChannelId(ChatPlatform.DISCORD, "channel"))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(1L, new ChannelMappingRequest(ChatPlatform.DISCORD, "channel", 9L));

        verify(repository).saveAndFlush(argThat(mapping ->
                mapping.getCreatedBy() == user && mapping.getWorkspace() == member.getWorkspace()));
    }
}
