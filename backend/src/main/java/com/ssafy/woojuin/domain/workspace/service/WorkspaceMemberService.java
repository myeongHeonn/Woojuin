package com.ssafy.woojuin.domain.workspace.service;

import com.ssafy.woojuin.domain.workspace.dto.UpdateMemberRoleRequest;
import com.ssafy.woojuin.domain.workspace.dto.WorkspaceMemberResponse;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceBan;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberActivity;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberActivityType;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceRole;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceLastOwnerException;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceMemberNotFoundException;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceMemberRequiredException;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceNotFoundException;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceOwnerRequiredException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceBanRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberActivityRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import com.ssafy.woojuin.global.sse.WorkspaceChangedEvent;
import com.ssafy.woojuin.global.sse.WorkspaceEventType;
import com.ssafy.woojuin.global.sse.WorkspaceMemberAction;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * OWNER가 0명이 되는 상태를 항상 막는다 (마지막 OWNER 강등/제거 금지 — 사용자 확인 완료).
 * 멤버 목록 조회는 OWNER/MEMBER 구분 없이 워크스페이스 멤버라면 누구나 가능하다.
 */
@Service
public class WorkspaceMemberService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceBanRepository workspaceBanRepository;
    private final WorkspaceMemberActivityRepository workspaceMemberActivityRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WorkspaceMemberService(WorkspaceRepository workspaceRepository,
                                   WorkspaceMemberRepository workspaceMemberRepository,
                                   WorkspaceBanRepository workspaceBanRepository,
                                   WorkspaceMemberActivityRepository workspaceMemberActivityRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceBanRepository = workspaceBanRepository;
        this.workspaceMemberActivityRepository = workspaceMemberActivityRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> list(Long workspaceId, Long requesterId) {
        findWorkspace(workspaceId);
        findMembership(workspaceId, requesterId);

        return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .map(WorkspaceMemberResponse::of)
                .toList();
    }

    @Transactional
    public WorkspaceMemberResponse updateRole(Long workspaceId, Long targetUserId, Long requesterId,
                                               UpdateMemberRoleRequest request) {
        findWorkspace(workspaceId);
        requireOwner(workspaceId, requesterId);
        WorkspaceMember target = findTargetMembership(workspaceId, targetUserId);

        if (target.getRole() == WorkspaceRole.OWNER && request.role() != WorkspaceRole.OWNER) {
            requireNotLastOwner(workspaceId);
        }

        target.updateRole(request.role());
        eventPublisher.publishEvent(WorkspaceChangedEvent.of(workspaceId, WorkspaceEventType.MEMBER));
        return WorkspaceMemberResponse.of(target);
    }

    /**
     * 활동 이력 저장은 멤버십 삭제와 같은 트랜잭션 안에서 실행된다 — 저장에 실패하면
     * 예외를 여기서 잡지 않고 그대로 전파해 트랜잭션 전체(멤버십 삭제, 재입장 차단 등록 포함)가
     * 함께 롤백되도록 한다. 이력만 빠진 채 멤버십만 바뀌는 상태를 만들지 않기 위함이다.
     */
    @Transactional
    public void remove(Long workspaceId, Long targetUserId, Long requesterId) {
        Workspace workspace = findWorkspace(workspaceId);
        WorkspaceMember target = findTargetMembership(workspaceId, targetUserId);

        WorkspaceMemberActivityType activityType =
                requesterId.equals(targetUserId) ? WorkspaceMemberActivityType.LEFT : WorkspaceMemberActivityType.KICKED;
        if (activityType == WorkspaceMemberActivityType.KICKED) {
            requireOwner(workspaceId, requesterId);
        }
        if (target.getRole() == WorkspaceRole.OWNER) {
            requireNotLastOwner(workspaceId);
        }

        if (activityType == WorkspaceMemberActivityType.KICKED) {
            workspaceBanRepository.save(WorkspaceBan.builder().workspace(workspace).user(target.getUser()).build());
        }
        workspaceMemberActivityRepository.save(
                WorkspaceMemberActivity.builder().workspace(workspace).user(target.getUser()).type(activityType).build());
        workspaceMemberRepository.delete(target);
        eventPublisher.publishEvent(WorkspaceChangedEvent.ofMemberAction(workspaceId,
                activityType == WorkspaceMemberActivityType.KICKED
                        ? WorkspaceMemberAction.KICKED : WorkspaceMemberAction.LEFT));
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    }

    private WorkspaceMember findMembership(Long workspaceId, Long userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceMemberRequiredException(workspaceId));
    }

    /** 대상 유저에 대한 조회. 요청자 본인의 멤버십 여부(403)와 달리, 대상이 없으면 404다. */
    private WorkspaceMember findTargetMembership(Long workspaceId, Long userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(workspaceId, userId));
    }

    private void requireOwner(Long workspaceId, Long userId) {
        WorkspaceMember member = findMembership(workspaceId, userId);
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new WorkspaceOwnerRequiredException(workspaceId);
        }
    }

    private void requireNotLastOwner(Long workspaceId) {
        if (workspaceMemberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
            throw new WorkspaceLastOwnerException(workspaceId);
        }
    }
}
