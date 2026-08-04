package com.ssafy.woojuin.domain.workspace.service;

import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.item.service.ItemActivityService;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberLastSeen;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceMemberRequiredException;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceNotFoundException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberLastSeenRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 워크스페이스의 아이템 활동을 마지막으로 확인한 시각을 조회/갱신하고,
 * 그 시각 이후 새 아이템 활동이 있는지 판정한다.
 */
@Service
public class WorkspaceItemActivityService {

    /**
     * "한 번도 확인한 적 없음"을 나타내는 하한값. {@code OffsetDateTime.MIN}을 쓰면 PostgreSQL
     * timestamptz 표현 범위(기원전 4713년~)를 훨씬 벗어나 "timestamp out of range" 쿼리 에러가
     * 난다 — 실사용 데이터가 존재할 수 없는 과거 시점이면 충분하므로 유닉스 epoch로 대신한다.
     */
    static final OffsetDateTime NEVER_SEEN = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceMemberLastSeenRepository workspaceMemberLastSeenRepository;
    private final UserRepository userRepository;
    private final ItemActivityService itemActivityService;

    public WorkspaceItemActivityService(WorkspaceRepository workspaceRepository,
                                         WorkspaceMemberRepository workspaceMemberRepository,
                                         WorkspaceMemberLastSeenRepository workspaceMemberLastSeenRepository,
                                         UserRepository userRepository,
                                         ItemActivityService itemActivityService) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceMemberLastSeenRepository = workspaceMemberLastSeenRepository;
        this.userRepository = userRepository;
        this.itemActivityService = itemActivityService;
    }

    @Transactional(readOnly = true)
    public boolean hasNewActivity(Long workspaceId, Long userId) {
        verifyAccess(workspaceId, userId);
        OffsetDateTime since = workspaceMemberLastSeenRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMemberLastSeen::getLastSeenAt)
                .orElse(NEVER_SEEN);
        return itemActivityService.hasActivitySince(workspaceId, since);
    }

    @Transactional
    public void updateLastSeen(Long workspaceId, Long userId) {
        verifyAccess(workspaceId, userId);
        workspaceMemberLastSeenRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .ifPresentOrElse(
                        existing -> existing.updateLastSeenAt(OffsetDateTime.now()),
                        () -> workspaceMemberLastSeenRepository.save(WorkspaceMemberLastSeen.builder()
                                .workspace(workspaceRepository.getReferenceById(workspaceId))
                                .user(userRepository.getReferenceById(userId))
                                .lastSeenAt(OffsetDateTime.now())
                                .build()));
    }

    /** 403(멤버 아님)과 404(워크스페이스 없음)를 구분한다. */
    private void verifyAccess(Long workspaceId, Long userId) {
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId).isPresent()) {
            return;
        }
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new WorkspaceNotFoundException(workspaceId);
        }
        throw new WorkspaceMemberRequiredException(workspaceId);
    }
}
