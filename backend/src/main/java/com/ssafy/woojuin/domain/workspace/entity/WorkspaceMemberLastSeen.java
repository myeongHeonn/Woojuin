package com.ssafy.woojuin.domain.workspace.entity;

import com.ssafy.woojuin.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 사용자가 워크스페이스의 멤버 활동 피드를 마지막으로 확인한 시각.
 * 행이 없으면 "한 번도 확인한 적 없음"을 뜻한다 — nullable 컬럼으로 표현하지 않고
 * WorkspaceBan/WorkspaceMemberActivity와 같은 방식(존재 자체가 상태)을 따른다.
 */
@Entity
@Table(name = "workspace_member_last_seens",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMemberLastSeen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Builder
    public WorkspaceMemberLastSeen(Workspace workspace, User user, OffsetDateTime lastSeenAt) {
        this.workspace = workspace;
        this.user = user;
        this.lastSeenAt = lastSeenAt;
    }

    public void updateLastSeenAt(OffsetDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
