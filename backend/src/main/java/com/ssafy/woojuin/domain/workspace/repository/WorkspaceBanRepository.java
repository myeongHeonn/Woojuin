package com.ssafy.woojuin.domain.workspace.repository;

import com.ssafy.woojuin.domain.workspace.entity.WorkspaceBan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceBanRepository extends JpaRepository<WorkspaceBan, Long> {

    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
