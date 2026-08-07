package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.exception.UserNotFoundException;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.auth.session.SessionRevocationStore;
import com.ssafy.woojuin.domain.auth.session.UserSessionStore;
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
    private final UserSessionStore sessionStore;
    private final SessionRevocationStore revocationStore;

    public UserWithdrawalService(
            UserRepository userRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceInvitationRepository workspaceInvitationRepository,
            NotificationTokenRepository notificationTokenRepository,
            UserSessionStore sessionStore,
            SessionRevocationStore revocationStore) {
        this.userRepository = userRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceInvitationRepository = workspaceInvitationRepository;
        this.notificationTokenRepository = notificationTokenRepository;
        this.sessionStore = sessionStore;
        this.revocationStore = revocationStore;
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        lockTeamsWithoutAnotherActiveOwner(userId);
        notificationTokenRepository.deleteByUserId(userId);
        // 모든 기기의 세션을 지우고 폐기 목록에 올린다 — 탈퇴자가 들고 있는 access token 도
        // 즉시 막힌다(필터가 탈퇴 여부도 보지만, 폐기가 더 싸게 먼저 걸린다)
        sessionStore.deleteAll(userId).forEach(revocationStore::revoke);
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
