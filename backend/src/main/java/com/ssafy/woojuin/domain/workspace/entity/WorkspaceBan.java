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
 * 워크스페이스에서 강제 추방된 유저 기록 — 존재 자체가 "재입장 금지"를 뜻한다.
 * 자진 탈퇴(LEFT)는 여기 들어가지 않는다. WorkspaceMember와 분리한 이유: 멤버십(현재 누가
 * 있는지)과 재입장 차단(과거에 누가 쫓겨났는지)은 서로 다른 질문이고, 가입/탈퇴/추방 자체의
 * 표시용 이력은 별도의 활동 이력 엔티티(S15P11C105-401)가 맡는다 — 여기 사유 필드를 또 두면
 * 같은 정보가 두 곳에서 어긋날 수 있다.
 */
@Entity
@Table(name = "workspace_bans", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "banned_at", nullable = false)
    private OffsetDateTime bannedAt;

    @Builder
    public WorkspaceBan(Workspace workspace, User user) {
        this.workspace = workspace;
        this.user = user;
        this.bannedAt = OffsetDateTime.now();
    }
}
