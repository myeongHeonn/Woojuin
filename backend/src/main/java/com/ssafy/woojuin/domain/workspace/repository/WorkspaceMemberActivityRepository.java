package com.ssafy.woojuin.domain.workspace.repository;

import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberActivityRepository extends JpaRepository<WorkspaceMemberActivity, Long> {

    List<WorkspaceMemberActivity> findByWorkspaceIdOrderByOccurredAtDesc(Long workspaceId);
}
