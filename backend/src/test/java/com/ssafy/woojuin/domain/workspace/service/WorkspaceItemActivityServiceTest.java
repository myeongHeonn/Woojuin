package com.ssafy.woojuin.domain.workspace.service;

import com.ssafy.woojuin.domain.auth.entity.AuthProvider;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.item.service.ItemActivityService;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberLastSeen;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceRole;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceType;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceMemberRequiredException;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceNotFoundException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberLastSeenRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceItemActivityServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private WorkspaceMemberLastSeenRepository workspaceMemberLastSeenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ItemActivityService itemActivityService;

    @InjectMocks
    private WorkspaceItemActivityService workspaceItemActivityService;

    private User user(Long id) {
        User user = User.builder()
                .email("test" + id + "@woojuin.com")
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .nickname("유저" + id)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Workspace workspace(Long id, User creator) {
        Workspace workspace = Workspace.builder().name("우리팀").type(WorkspaceType.TEAM).createdBy(creator).build();
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private WorkspaceMember member(Workspace ws, User user, WorkspaceRole role) {
        return WorkspaceMember.builder().workspace(ws).user(user).role(role).build();
    }

    @Test
    @DisplayName("멤버가 아니면 새 활동 조회 시 403 예외를 던진다")
    void hasNewActivity_notMember_throwsMemberRequired() {
        User owner = user(1L);
        Workspace ws = workspace(10L, owner);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 99L)).thenReturn(Optional.empty());
        when(workspaceRepository.existsById(10L)).thenReturn(true);

        assertThatThrownBy(() -> workspaceItemActivityService.hasNewActivity(10L, 99L))
                .isInstanceOf(WorkspaceMemberRequiredException.class);
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스면 새 활동 조회 시 404 예외를 던진다")
    void hasNewActivity_workspaceNotFound_throwsNotFound() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(999L, 1L)).thenReturn(Optional.empty());
        when(workspaceRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> workspaceItemActivityService.hasNewActivity(999L, 1L))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    @DisplayName("마지막 확인 시각 기록이 있으면 그 시각 이후 활동 여부를 그대로 반환한다")
    void hasNewActivity_lastSeenExists_delegatesWithLastSeenAt() {
        User owner = user(1L);
        Workspace ws = workspace(10L, owner);
        WorkspaceMember membership = member(ws, owner, WorkspaceRole.OWNER);
        OffsetDateTime lastSeenAt = OffsetDateTime.now().minusHours(2);
        WorkspaceMemberLastSeen lastSeen =
                WorkspaceMemberLastSeen.builder().workspace(ws).user(owner).lastSeenAt(lastSeenAt).build();
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 1L)).thenReturn(Optional.of(membership));
        when(workspaceMemberLastSeenRepository.findByWorkspaceIdAndUserId(10L, 1L)).thenReturn(Optional.of(lastSeen));
        when(itemActivityService.hasActivitySince(10L, lastSeenAt)).thenReturn(true);

        boolean result = workspaceItemActivityService.hasNewActivity(10L, 1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("한 번도 확인한 적 없으면 아이템이 하나라도 있는 한 새 활동으로 판정한다")
    void hasNewActivity_neverSeen_delegatesWithMinDateTime() {
        User owner = user(1L);
        Workspace ws = workspace(10L, owner);
        WorkspaceMember membership = member(ws, owner, WorkspaceRole.OWNER);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 1L)).thenReturn(Optional.of(membership));
        when(workspaceMemberLastSeenRepository.findByWorkspaceIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(itemActivityService.hasActivitySince(10L, OffsetDateTime.MIN)).thenReturn(true);

        boolean result = workspaceItemActivityService.hasNewActivity(10L, 1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("멤버가 아니면 확인 시각 갱신 시 403 예외를 던지고 저장하지 않는다")
    void updateLastSeen_notMember_throwsMemberRequired() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 99L)).thenReturn(Optional.empty());
        when(workspaceRepository.existsById(10L)).thenReturn(true);

        assertThatThrownBy(() -> workspaceItemActivityService.updateLastSeen(10L, 99L))
                .isInstanceOf(WorkspaceMemberRequiredException.class);

        verify(workspaceMemberLastSeenRepository, never()).save(any());
    }

    @Test
    @DisplayName("확인 기록이 없으면 새로 생성한다")
    void updateLastSeen_noExistingRecord_createsNew() {
        User owner = user(1L);
        Workspace ws = workspace(10L, owner);
        WorkspaceMember membership = member(ws, owner, WorkspaceRole.OWNER);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 1L)).thenReturn(Optional.of(membership));
        when(workspaceMemberLastSeenRepository.findByWorkspaceIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(workspaceRepository.getReferenceById(10L)).thenReturn(ws);
        when(userRepository.getReferenceById(1L)).thenReturn(owner);

        workspaceItemActivityService.updateLastSeen(10L, 1L);

        ArgumentCaptor<WorkspaceMemberLastSeen> captor = ArgumentCaptor.forClass(WorkspaceMemberLastSeen.class);
        verify(workspaceMemberLastSeenRepository).save(captor.capture());
        assertThat(captor.getValue().getWorkspace()).isEqualTo(ws);
        assertThat(captor.getValue().getUser()).isEqualTo(owner);
        assertThat(captor.getValue().getLastSeenAt()).isAfter(OffsetDateTime.now().minusMinutes(1));
    }

    @Test
    @DisplayName("확인 기록이 이미 있으면 시각만 갱신하고 새로 저장하지 않는다")
    void updateLastSeen_existingRecord_updatesTimestamp() {
        User owner = user(1L);
        Workspace ws = workspace(10L, owner);
        WorkspaceMember membership = member(ws, owner, WorkspaceRole.OWNER);
        WorkspaceMemberLastSeen existing = WorkspaceMemberLastSeen.builder()
                .workspace(ws).user(owner).lastSeenAt(OffsetDateTime.now().minusDays(1)).build();
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 1L)).thenReturn(Optional.of(membership));
        when(workspaceMemberLastSeenRepository.findByWorkspaceIdAndUserId(10L, 1L)).thenReturn(Optional.of(existing));

        workspaceItemActivityService.updateLastSeen(10L, 1L);

        assertThat(existing.getLastSeenAt()).isAfter(OffsetDateTime.now().minusMinutes(1));
        verify(workspaceMemberLastSeenRepository, never()).save(any());
    }
}
