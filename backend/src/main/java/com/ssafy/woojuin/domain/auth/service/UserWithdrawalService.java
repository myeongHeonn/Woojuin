package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.exception.UserNotFoundException;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.notification.repository.NotificationTokenRepository;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceRole;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceType;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceInvitationRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInvitationRepository workspaceInvitationRepository;
    private final NotificationTokenRepository notificationTokenRepository;
    private final RefreshTokenStore refreshTokenStore;

    public UserWithdrawalService(
            UserRepository userRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceInvitationRepository workspaceInvitationRepository,
            NotificationTokenRepository notificationTokenRepository,
            RefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceInvitationRepository = workspaceInvitationRepository;
        this.notificationTokenRepository = notificationTokenRepository;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        lockTeamsWithoutAnotherActiveOwner(userId);
        notificationTokenRepository.deleteByUserId(userId);
        refreshTokenStore.delete(userId);
        user.withdraw();
    }

    private void lockTeamsWithoutAnotherActiveOwner(Long userId) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(userId);
        for (WorkspaceMember membership : memberships) {
            if (membership.getRole() == WorkspaceRole.MEMBER) {
                workspaceMemberRepository.delete(membership);
                continue;
            }
            if (membership.getWorkspace().getType() != WorkspaceType.TEAM) {
                continue;
            }

            Long workspaceId = membership.getWorkspace().getId();
            long otherActiveOwners = workspaceMemberRepository
                    .countActiveByWorkspaceIdAndRoleExcludingUser(
                            workspaceId, WorkspaceRole.OWNER, userId);
            if (otherActiveOwners > 0) {
                continue;
            }

            workspaceMemberRepository.deleteByWorkspaceIdAndUserIdNot(workspaceId, userId);
            workspaceInvitationRepository.deleteByWorkspaceId(workspaceId);
        }
    }
}
