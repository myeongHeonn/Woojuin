package com.ssafy.woojuin.domain.item;

import com.ssafy.woojuin.global.common.BaseTimeEntity;
import com.ssafy.woojuin.global.common.ItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * items 테이블 (ERD 기준). workspace_id/created_by는 워크스페이스/인증 도메인이
 * 아직 준비되지 않아 JPA 연관관계 없이 순수 FK 컬럼으로 둔다.
 */
@Getter
@Entity
@Table(name = "items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ItemType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ItemStatus status;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String url;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 500)
    private String s3Key;

    @Column(columnDefinition = "text")
    private String previewDescription;

    @Column(columnDefinition = "text")
    private String previewThumbnailUrl;

    private Long categoryId;

    @Column(nullable = false)
    private boolean favorite;

    private OffsetDateTime deletedAt;

    @Builder
    private Item(Long workspaceId, Long createdBy, ItemType type, String title, String url,
            String content, String s3Key) {
        this.workspaceId = workspaceId;
        this.createdBy = createdBy;
        this.type = type;
        this.status = ItemStatus.PROCESSING;
        this.title = title;
        this.url = url;
        this.content = content;
        this.s3Key = s3Key;
        this.favorite = false;
    }

    /**
     * null인 필드는 건드리지 않는다(부분 수정). content는 타입과 무관하게 수정 가능한데,
     * URL 아이템도 트랙 A/B가 모두 실패하면 사용자 메모로 폴백하기 때문이다(FR-020).
     */
    public void update(String title, String content) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
    }

    public void moveToTrash() {
        this.deletedAt = OffsetDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isTrashed() {
        return this.deletedAt != null;
    }
}
