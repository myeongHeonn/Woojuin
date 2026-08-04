package com.ssafy.woojuin.domain.workspace.service;

import com.ssafy.woojuin.domain.auth.entity.AuthProvider;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.workspace.dto.WorkspaceMemberActivityResponse;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberActivity;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberActivityType;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceRole;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceType;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceMemberRequiredException;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceNotFoundException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberActivityRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceMemberActivityQueryServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private WorkspaceMemberActivityRepository workspaceMemberActivityRepository;

    @InjectMocks
    private WorkspaceMemberActivityQueryService workspaceMemberActivityQueryService;

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

    @Test
    @DisplayName("멤버면 워크스페이스의 활동 이력을 최신순으로 조회할 수 있다")
    void list_member_returnsActivitiesNewestFirst() {
        User owner = user(1L);
        User target = user(2L);
        Workspace ws = workspace(10L, owner);
        WorkspaceMember ownerMembership =
                WorkspaceMember.builder().workspace(ws).user(owner).role(WorkspaceRole.OWNER).build();
        WorkspaceMemberActivity kicked = WorkspaceMemberActivity.builder()
                .workspace(ws).user(target).type(WorkspaceMemberActivityType.KICKED).build();
        WorkspaceMemberActivity joined = WorkspaceMemberActivity.builder()
                .workspace(ws).user(target).type(WorkspaceMemberActivityType.JOINED).build();
        when(workspaceRepository.existsById(10L)).thenReturn(true);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 1L)).thenReturn(Optional.of(ownerMembership));
        when(workspaceMemberActivityRepository.findByWorkspaceIdOrderByOccurredAtDesc(10L))
                .thenReturn(List.of(kicked, joined));

        List<WorkspaceMemberActivityResponse> result = workspaceMemberActivityQueryService.list(10L, 1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(WorkspaceMemberActivityType.KICKED);
        assertThat(result.get(1).type()).isEqualTo(WorkspaceMemberActivityType.JOINED);
        assertThat(result.get(0).nickname()).isEqualTo("유저2");
    }

    @Test
    @DisplayName("탈퇴한 사용자의 활동은 탈퇴 사용자 표시로 대체한다")
    void list_withdrawnUser_masksNickname() {
        User owner = user(1L);
        User withdrawn = user(2L);
        withdrawn.withdraw();
        Workspace ws = workspace(10L, owner);
        WorkspaceMember ownerMembership =
                WorkspaceMember.builder().workspace(ws).user(owner).role(WorkspaceRole.OWNER).build();
        WorkspaceMemberActivity left = WorkspaceMemberActivity.builder()
                .workspace(ws).user(withdrawn).type(WorkspaceMemberActivityType.LEFT).build();
        when(workspaceRepository.existsById(10L)).thenReturn(true);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 1L)).thenReturn(Optional.of(ownerMembership));
        when(workspaceMemberActivityRepository.findByWorkspaceIdOrderByOccurredAtDesc(10L)).thenReturn(List.of(left));

        List<WorkspaceMemberActivityResponse> result = workspaceMemberActivityQueryService.list(10L, 1L);

        assertThat(result.get(0).nickname()).isEqualTo("탈퇴한 사용자");
        assertThat(result.get(0).withdrawn()).isTrue();
    }

    @Test
    @DisplayName("멤버가 아니면 활동 이력 조회 시 403 예외를 던진다")
    void list_notMember_throwsMemberRequired() {
        when(workspaceRepository.existsById(10L)).thenReturn(true);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceMemberActivityQueryService.list(10L, 99L))
                .isInstanceOf(WorkspaceMemberRequiredException.class);
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스면 활동 이력 조회 시 404 예외를 던진다")
    void list_workspaceNotFound_throwsNotFound() {
        when(workspaceRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> workspaceMemberActivityQueryService.list(999L, 1L))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }
}
