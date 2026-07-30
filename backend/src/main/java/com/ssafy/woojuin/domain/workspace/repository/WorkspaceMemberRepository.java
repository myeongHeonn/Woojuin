package com.ssafy.woojuin.domain.workspace.repository;

import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    List<WorkspaceMember> findByWorkspaceId(Long workspaceId);

    List<WorkspaceMember> findByUserId(Long userId);

    long countByUserId(Long userId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    Optional<WorkspaceMember> findFirstByWorkspaceIdAndRoleAndUserDeletedAtIsNullOrderByJoinedAtAscIdAsc(
            Long workspaceId, WorkspaceRole role);

    long countByWorkspaceIdAndRole(Long workspaceId, WorkspaceRole role);

    @Query("""
            select count(member)
            from WorkspaceMember member
            where member.workspace.id = :workspaceId
              and member.role = :role
              and member.user.id <> :excludedUserId
              and member.user.deletedAt is null
            """)
    long countActiveByWorkspaceIdAndRoleExcludingUser(
            @Param("workspaceId") Long workspaceId,
            @Param("role") WorkspaceRole role,
            @Param("excludedUserId") Long excludedUserId);

    void deleteByWorkspaceIdAndUserIdNot(Long workspaceId, Long userId);
}
