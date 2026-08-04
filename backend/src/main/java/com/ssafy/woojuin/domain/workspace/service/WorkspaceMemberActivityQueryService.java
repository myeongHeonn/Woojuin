package com.ssafy.woojuin.domain.workspace.service;

import com.ssafy.woojuin.domain.workspace.dto.WorkspaceMemberActivityResponse;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceMemberRequiredException;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceNotFoundException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberActivityRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 워크스페이스 멤버 활동 이력(가입/탈퇴/추방) 조회. */
@Service
public class WorkspaceMemberActivityQueryService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceMemberActivityRepository workspaceMemberActivityRepository;

    public WorkspaceMemberActivityQueryService(WorkspaceRepository workspaceRepository,
                                                WorkspaceMemberRepository workspaceMemberRepository,
                                                WorkspaceMemberActivityRepository workspaceMemberActivityRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceMemberActivityRepository = workspaceMemberActivityRepository;
    }

    /**
     * 조회자가 이 워크스페이스에 참여하기 전의 활동(자신이 없던 시절 다른 사람의
     * 가입/탈퇴/추방)은 보여주지 않는다 — 본인의 joinedAt 이후 것만 반환한다.
     */
    @Transactional(readOnly = true)
    public List<WorkspaceMemberActivityResponse> list(Long workspaceId, Long requesterId) {
        findWorkspace(workspaceId);
        WorkspaceMember requesterMembership = findMembership(workspaceId, requesterId);
        return workspaceMemberActivityRepository
                .findByWorkspaceIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                        workspaceId, requesterMembership.getJoinedAt())
                .stream()
                .map(WorkspaceMemberActivityResponse::of)
                .toList();
    }

    private void findWorkspace(Long workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new WorkspaceNotFoundException(workspaceId);
        }
    }

    private WorkspaceMember findMembership(Long workspaceId, Long userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceMemberRequiredException(workspaceId));
    }
}
