package com.ssafy.woojuin.domain.integration.entity;

import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.workspace.entity.Workspace;
import com.ssafy.woojuin.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_channel_mappings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"platform", "channel_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatChannelMapping extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatPlatform platform;

    @Column(name = "channel_id", nullable = false, length = 100)
    private String channelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    public ChatChannelMapping(ChatPlatform platform, String channelId, Workspace workspace, User createdBy) {
        this.platform = platform;
        this.channelId = channelId;
        this.workspace = workspace;
        this.createdBy = createdBy;
    }
}
