package com.ssafy.woojuin.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * User:Token = 1:N (한 유저가 여러 기기에서 로그인할 수 있다).
 * token 자체가 기기/브라우저 설치 단위로 유일하므로 전역 UNIQUE — 같은 토큰으로 다시
 * 등록되면(재로그인 등) 새로 만들지 않고 소유자만 갱신한다({@link #reassignTo}).
 */
@Getter
@Entity
@Table(name = "notification_tokens", uniqueConstraints = @UniqueConstraint(columnNames = "token"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    public NotificationToken(Long userId, String token, String deviceInfo) {
        this.userId = userId;
        this.token = token;
        this.deviceInfo = deviceInfo;
        this.createdAt = OffsetDateTime.now();
    }

    public void reassignTo(Long userId, String deviceInfo) {
        this.userId = userId;
        this.deviceInfo = deviceInfo;
    }
}
