package com.ssafy.woojuin.domain.auth.entity;

import com.ssafy.woojuin.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * LOCAL 유저는 passwordHash 필수 + providerId NULL, OAuth 유저는 반대.
 * (ERD 설계 노트 CHECK 제약 참고, 검증은 서비스 레이어에서 수행)
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /** 가입 시 자동 생성되는 PERSONAL 워크스페이스의 id. 생성 전에는 null. */
    @Column(name = "personal_workspace_id")
    private Long personalWorkspaceId;

    /** 개인 스페이스 최초 이용 안내 완료 여부. 기존 사용자 행은 false로 안전하게 채운다. */
    @Column(name = "personal_tutorial_completed", nullable = false,
            columnDefinition = "boolean default false")
    private boolean personalTutorialCompleted;

    /** 공유 워크스페이스 최초 이용 안내 완료 여부. 개인 안내와 독립적으로 관리한다. */
    @Column(name = "shared_workspace_tutorial_completed", nullable = false,
            columnDefinition = "boolean default false")
    private boolean sharedWorkspaceTutorialCompleted;

    /** 프로필 아바타 색상. 가입 시 기본값은 WHITE, 나중에 프로필 설정에서 바꿀 수 있게 할 예정. */
    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_color", nullable = false, length = 20)
    private AvatarColor avatarColor = AvatarColor.WHITE;

    @Builder
    public User(String email, String passwordHash, AuthProvider provider, String providerId,
                boolean emailVerified, String nickname, String profileImageUrl) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerId = providerId;
        this.emailVerified = emailVerified;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateProfile(String nickname, String profileImageUrl, AvatarColor avatarColor) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.avatarColor = avatarColor;
    }

    public void assignPersonalWorkspace(Long workspaceId) {
        this.personalWorkspaceId = workspaceId;
    }

    public void completeTutorial(TutorialType tutorialType) {
        if (tutorialType == TutorialType.PERSONAL) {
            this.personalTutorialCompleted = true;
            return;
        }
        this.sharedWorkspaceTutorialCompleted = true;
    }

    /**
     * 탈퇴 처리 — 행은 남기고(소프트 삭제) <b>로그인 식별자만 파기</b>한다.
     *
     * <p>식별자를 지우는 이유는 두 가지다. 하나는 고지 이행: 개인정보처리방침이 회원 정보(이메일
     * 등)의 보유기간을 "회원 탈퇴 시까지"로 밝히고 있는데, 식별자를 그대로 들고 있으면 그 고지와
     * 어긋난다. 다른 하나는 재가입: {@code uk_users_provider UNIQUE (provider, provider_id)} 가
     * 탈퇴자의 구글 sub 를 계속 붙들고 있으면 <b>같은 구글 계정은 두 번 다시 가입할 수 없다</b> —
     * 실패도 조용해서 사용자는 로그인 화면만 되돌아 본다.
     *
     * <p>파기 후 같은 구글 계정으로 로그인하면 조회가 빈 결과가 되어 자연스럽게 <b>새 계정</b>으로
     * 가입된다. 옛 아이템은 옛 행에 소프트 삭제 상태로 남아 되살아나지 않는다 — 탈퇴한 사람이
     * 기대하는 결과이기도 하다.
     *
     * <p>{@code nickname} 은 남긴다 — 로그인 식별자가 아니어서 재가입을 막지 않기 때문이고,
     * 이 변경의 범위를 로그인 식별자로 한정한 것이다. 표시 때문은 아니다: 탈퇴자의 닉네임을 화면에
     * 내보내는 경로는 없다(WorkspaceMemberResponse·WorkspaceMemberActivityResponse 가 둘 다
     * "탈퇴한 사용자"로 바꿔 내보낸다). 그래서 닉네임까지 파기해도 화면은 달라지지 않으며,
     * 파기 여부는 순수하게 개인정보 보유 정책의 판단이다.
     */
    public void withdraw() {
        if (this.deletedAt != null) {
            return;
        }
        this.deletedAt = OffsetDateTime.now();
        this.email = withdrawnPlaceholderEmail();
        this.providerId = null;
    }

    /**
     * 파기한 이메일 자리에 넣을 값. email 은 NOT NULL 이라 비울 수 없어 대체값이 필요하다.
     *
     * <p>{@code .invalid} 는 RFC 2606 이 예약한 TLD 로 실제 주소가 될 수 없다 — 이 값이 어딘가로
     * 새어 나가도 남의 메일함에 닿지 않는다. id 를 섞는 것은 탈퇴자가 여럿일 때 값이 겹치지 않게
     * 하려는 것이다(영속화 전 호출까지 대비해 id 가 없으면 임의값으로 떨어진다).
     */
    private String withdrawnPlaceholderEmail() {
        String token = this.id != null ? String.valueOf(this.id) : UUID.randomUUID().toString();
        return "withdrawn+" + token + "@woojuin.invalid";
    }

    public boolean isWithdrawn() {
        return this.deletedAt != null;
    }
}
