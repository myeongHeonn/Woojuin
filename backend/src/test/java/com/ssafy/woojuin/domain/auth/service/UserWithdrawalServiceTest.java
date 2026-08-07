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
    private com.ssafy.woojuin.domain.auth.session.UserSessionStore sessionStore;

    @Mock
    private com.ssafy.woojuin.domain.auth.session.SessionRevocationStore revocationStore;

    @InjectMocks
    private UserWithdrawalService userWithdrawalService;

    @Test
    void withdraw_marksUserAndDeletesAuthenticationTokens() {
        User user = user(1L);
        Workspace personalWorkspace = workspace(10L, user, WorkspaceType.PERSONAL);
        WorkspaceMember personalOwner = membership(personalWorkspace, user, WorkspaceRole.OWNER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByUserId(1L)).thenReturn(List.of(personalOwner));
        when(sessionStore.deleteAll(1L)).thenReturn(List.of("sid-a", "sid-b"));

        userWithdrawalService.withdraw(1L);

        assertThat(user.isWithdrawn()).isTrue();
        verify(notificationTokenRepository).deleteByUserId(1L);
        // 모든 세션이 지워지고 각 sid 가 폐기 목록에 올라 즉시 차단된다
        verify(sessionStore).deleteAll(1L);
        verify(revocationStore).revoke("sid-a");
        verify(revocationStore).revoke("sid-b");
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

        when(sessionStore.deleteAll(1L)).thenReturn(List.of());

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

        when(sessionStore.deleteAll(1L)).thenReturn(List.of());

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

        when(sessionStore.deleteAll(1L)).thenReturn(List.of());

        userWithdrawalService.withdraw(1L);

        verify(workspaceMemberRepository, never()).deleteByWorkspaceIdAndUserIdNot(20L, 1L);
        verify(workspaceInvitationRepository, never()).deleteByWorkspaceId(20L);
        assertThat(user.isWithdrawn()).isTrue();
    }

    @Test
    void withdraw_googleUser_scrubsLoginIdentifiersSoTheAccountCanSignUpAgain() {
        User user = googleUser(1L);
        Workspace personalWorkspace = workspace(10L, user, WorkspaceType.PERSONAL);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByUserId(1L))
                .thenReturn(List.of(membership(personalWorkspace, user, WorkspaceRole.OWNER)));
        when(sessionStore.deleteAll(1L)).thenReturn(List.of());

        userWithdrawalService.withdraw(1L);

        // provider_id 가 남으면 uk_users_provider 가 같은 구글 계정의 재가입을 영구히 막는다.
        assertThat(user.getProviderId()).isNull();
        assertThat(user.getEmail()).isEqualTo("withdrawn+1@woojuin.invalid");
        assertThat(user.isWithdrawn()).isTrue();
    }

    @Test
    void withdraw_unknownUser_throwsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userWithdrawalService.withdraw(99L))
                .isInstanceOf(UserNotFoundException.class);

        verify(notificationTokenRepository, never()).deleteByUserId(99L);
        verify(sessionStore, never()).deleteAll(99L);
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

    private User googleUser(Long id) {
        User user = User.builder()
                .email("real@gmail.com")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-sub-" + id)
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
