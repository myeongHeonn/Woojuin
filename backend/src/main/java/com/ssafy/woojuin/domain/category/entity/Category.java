package com.ssafy.woojuin.domain.category.entity;

import com.ssafy.woojuin.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리. 워크스페이스마다 별도로 존재하며(같은 이름이라도 다른 레코드), 기본 11개가
 * 시드된 뒤 사용자가 추가/수정/삭제한다.
 *
 * <p>workspaceId는 Item과 같은 방식으로 JPA 연관관계 없이 순수 FK 컬럼으로 둔다 —
 * 비동기 가공 파이프라인이 Workspace 엔티티를 로드하지 않고 id만으로 다루기 때문.
 */
@Getter
@Entity
@Table(name = "categories",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "name"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long workspaceId;

    @Column(nullable = false, length = 50)
    private String name;

    // 카드/별자리 색(hex, 예 "#C9B8FF"). 기존 데이터 호환을 위해 nullable로 두고,
    // 시드·생성 시 채우며 기존 행은 CategoryColorBackfill이 기동 시 채운다.
    @Column(length = 20)
    private String color;

    // AI 분류 입력용 판단 기준 설명. 색과 같은 이유로 nullable — 기본 카테고리는
    // CategoryDescriptionBackfill이 기동 시 채우고, 사용자 카테고리는 생성 후 비동기로
    // 생성된다(CategoryDescriptionGenerator). null이면 분류 시 이름으로 폴백한다.
    @Column(length = 500)
    private String description;

    @Builder
    private Category(Long workspaceId, String name, String color, String description) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.color = color;
        this.description = description;
    }

    /**
     * 이름이 바뀌면 설명은 옛 이름 기준이라 함께 비운다 — 분류가 stale한 설명에 끌려가는 것보다
     * 이름 폴백이 낫다. 새 설명은 호출부가 비동기로 다시 생성한다.
     */
    public void rename(String name) {
        if (!name.equals(this.name)) {
            this.description = null;
        }
        this.name = name;
    }

    /** null 인자는 "값 없음"이라 무시한다(기존 설명을 지우지 않음). */
    public void applyDescription(String description) {
        if (description != null && !description.isBlank()) {
            this.description = description;
        }
    }

    /** null 인자는 "값 없음"이라 무시한다(기존 색을 지우지 않음). */
    public void applyColor(String color) {
        if (color != null) {
            this.color = color;
        }
    }
}
