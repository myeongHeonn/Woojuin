package com.ssafy.woojuin.domain.workspace.repository;

import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMemberActivity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberActivityRepository extends JpaRepository<WorkspaceMemberActivity, Long> {

    /**
     * 조회자가 워크스페이스에 참여한 시각(since) 이후의 활동만 최신순으로 반환한다.
     * 참여 이전 이력(자신이 없던 시절 다른 사람의 가입/탈퇴/추방)은 보여주지 않는다.
     */
    List<WorkspaceMemberActivity> findByWorkspaceIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            Long workspaceId, OffsetDateTime since);
}
