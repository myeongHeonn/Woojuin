package com.ssafy.woojuin.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.auth.entity.AuthProvider;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.exception.UserNotFoundException;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.notification.repository.NotificationTokenRepository;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceRole;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceType;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceInvitationRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private WorkspaceInvitationRepository workspaceInvitationRepository;

    @Mock
    private NotificationTokenRepository notificationTokenRepository;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private UserWithdrawalService userWithdrawalService;

    @Test
    void withdraw_marksUserAndDeletesAuthenticationTokens() {
        User user = user(1L);
        Workspace personalWorkspace = workspace(10L, user, WorkspaceType.PERSONAL);
        WorkspaceMember personalOwner = membership(personalWorkspace, user, WorkspaceRole.OWNER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByUserId(1L)).thenReturn(List.of(personalOwner));

        userWithdrawalService.withdraw(1L);

        assertThat(user.isWithdrawn()).isTrue();
        verify(notificationTokenRepository).deleteByUserId(1L);
        verify(refreshTokenStore).delete(1L);
        verify(workspaceMemberRepository, never()).deleteByWorkspaceIdAndUserIdNot(10L, 1L);
        verify(workspaceInvitationRepository, never()).deleteByWorkspaceId(10L);
    }

    @Test
    void withdraw_soleTeamOwner_ejectsOtherMembersAndDeletesInvitations() {
        User user = user(1L);
        Workspace teamWorkspace = workspace(20L, user, WorkspaceType.TEAM);
        WorkspaceMember owner = membership(teamWorkspace, user, WorkspaceRole.OWNER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByUserId(1L)).thenReturn(List.of(owner));
        when(workspaceMemberRepository.countActiveByWorkspaceIdAndRoleExcludingUser(
                20L, WorkspaceRole.OWNER, 1L)).thenReturn(0L);

        userWithdrawalService.withdraw(1L);

        verify(workspaceMemberRepository).deleteByWorkspaceIdAndUserIdNot(20L, 1L);
        verify(workspaceInvitationRepository).deleteByWorkspaceId(20L);
    }

    @Test
    void withdraw_invitedMember_leavesWorkspaceImmediately() {
        User user = user(1L);
        User owner = user(2L);
        Workspace teamWorkspace = workspace(20L, owner, WorkspaceType.TEAM);
        WorkspaceMember member = membership(teamWorkspace, user, WorkspaceRole.MEMBER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByUserId(1L)).thenReturn(List.of(member));

        userWithdrawalService.withdraw(1L);

        verify(workspaceMemberRepository).delete(member);
        verify(workspaceMemberRepository, never())
                .deleteByWorkspaceIdAndUserIdNot(20L, 1L);
        verify(workspaceInvitationRepository, never()).deleteByWorkspaceId(20L);
    }

    @Test
    void withdraw_teamWithAnotherActiveOwner_keepsMembersAndInvitations() {
        User user = user(1L);
        Workspace teamWorkspace = workspace(20L, user, WorkspaceType.TEAM);
        WorkspaceMember owner = membership(teamWorkspace, user, WorkspaceRole.OWNER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByUserId(1L)).thenReturn(List.of(owner));
        when(workspaceMemberRepository.countActiveByWorkspaceIdAndRoleExcludingUser(
                20L, WorkspaceRole.OWNER, 1L)).thenReturn(1L);

        userWithdrawalService.withdraw(1L);

        verify(workspaceMemberRepository, never()).deleteByWorkspaceIdAndUserIdNot(20L, 1L);
        verify(workspaceInvitationRepository, never()).deleteByWorkspaceId(20L);
        assertThat(user.isWithdrawn()).isTrue();
    }

    @Test
    void withdraw_unknownUser_throwsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userWithdrawalService.withdraw(99L))
                .isInstanceOf(UserNotFoundException.class);

        verify(notificationTokenRepository, never()).deleteByUserId(99L);
        verify(refreshTokenStore, never()).delete(99L);
    }

    private User user(Long id) {
        User user = User.builder()
                .email("user" + id + "@woojuin.com")
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .nickname("우주인")
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Workspace workspace(Long id, User creator, WorkspaceType type) {
        Workspace workspace = Workspace.builder()
                .name("워크스페이스")
                .type(type)
                .createdBy(creator)
                .build();
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private WorkspaceMember membership(Workspace workspace, User user, WorkspaceRole role) {
        return WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(role)
                .build();
    }
}
