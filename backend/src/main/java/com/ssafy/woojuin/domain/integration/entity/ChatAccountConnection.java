package com.ssafy.woojuin.domain.integration.entity;

import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.global.common.BaseTimeEntity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_account_connections",
        uniqueConstraints = @UniqueConstraint(columnNames = {"platform", "external_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatAccountConnection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatPlatform platform;

    @Column(name = "external_user_id", nullable = false, length = 100)
    private String externalUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_workspace_id")
    private Workspace defaultWorkspace;

    @Column(name = "oauth_access_token", columnDefinition = "TEXT")
    private String oauthAccessToken;

    @Column(name = "oauth_refresh_token", columnDefinition = "TEXT")
    private String oauthRefreshToken;

    @Column(name = "oauth_expires_at")
    private java.time.OffsetDateTime oauthExpiresAt;

    public ChatAccountConnection(ChatPlatform platform, String externalUserId, User user) {
        this.platform = platform;
        this.externalUserId = externalUserId;
        this.user = user;
    }

    public void changeDefaultWorkspace(Workspace workspace) {
        this.defaultWorkspace = workspace;
    }

    public void connectOAuth(String accessToken, String refreshToken, java.time.OffsetDateTime expiresAt) {
        this.oauthAccessToken = accessToken;
        this.oauthRefreshToken = refreshToken;
        this.oauthExpiresAt = expiresAt;
    }

    public boolean hasOAuthConnection() {
        return oauthAccessToken != null && !oauthAccessToken.isBlank();
    }
}
