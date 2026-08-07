package com.ssafy.woojuin.domain.workspace.repository;

import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberLastSeen;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberLastSeenRepository extends JpaRepository<WorkspaceMemberLastSeen, Long> {

    Optional<WorkspaceMemberLastSeen> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
