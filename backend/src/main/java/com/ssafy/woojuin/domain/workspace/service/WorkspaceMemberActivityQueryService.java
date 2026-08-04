package com.ssafy.woojuin.domain.workspace.service;

import com.ssafy.woojuin.domain.workspace.dto.WorkspaceMemberActivityResponse;
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

    @Transactional(readOnly = true)
    public List<WorkspaceMemberActivityResponse> list(Long workspaceId, Long requesterId) {
        findWorkspace(workspaceId);
        findMembership(workspaceId, requesterId);
        return workspaceMemberActivityRepository.findByWorkspaceIdOrderByOccurredAtDesc(workspaceId).stream()
                .map(WorkspaceMemberActivityResponse::of)
                .toList();
    }

    private void findWorkspace(Long workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new WorkspaceNotFoundException(workspaceId);
        }
    }

    private void findMembership(Long workspaceId, Long userId) {
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId).isEmpty()) {
            throw new WorkspaceMemberRequiredException(workspaceId);
        }
    }
}
