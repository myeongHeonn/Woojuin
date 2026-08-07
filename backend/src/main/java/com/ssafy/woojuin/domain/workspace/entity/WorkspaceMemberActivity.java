package com.ssafy.woojuin.domain.workspace.entity;

import com.ssafy.woojuin.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 멤버 활동 피드(가입/탈퇴/추방)의 표시용 이력. WorkspaceMember(현재 누가 있는지)나
 * WorkspaceBan(누가 재입장 금지인지)과는 별개다 — 이 엔티티는 오직 "무슨 일이
 * 언제 있었는지" 화면에 나열하기 위한 로그다. 그래서 멤버가 나중에 다시 가입/탈퇴를
 * 반복해도 매번 새 행이 쌓인다(다른 두 엔티티처럼 최신 상태 하나만 갖지 않는다).
 */
@Entity
@Table(name = "workspace_member_activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMemberActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    /** 이 사건의 대상 — 가입/탈퇴/추방된 사람. 누가 추방시켰는지는 기록하지 않는다(피드에 안 씀). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceMemberActivityType type;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Builder
    public WorkspaceMemberActivity(Workspace workspace, User user, WorkspaceMemberActivityType type) {
        this.workspace = workspace;
        this.user = user;
        this.type = type;
        this.occurredAt = OffsetDateTime.now();
    }
}
