package com.ssafy.woojuin.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인앱 알림함 항목. 아이템이 DONE으로 전이될 때만 생성된다(AGENTS.md 규칙 6, FAILED는 생략). */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    public Notification(Long userId, Long itemId, String message) {
        this.userId = userId;
        this.itemId = itemId;
        this.message = message;
        this.createdAt = OffsetDateTime.now();
    }
}
