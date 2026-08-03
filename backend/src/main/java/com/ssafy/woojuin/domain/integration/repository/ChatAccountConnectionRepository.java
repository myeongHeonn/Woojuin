package com.ssafy.woojuin.domain.integration.repository;

import com.ssafy.woojuin.domain.integration.entity.ChatAccountConnection;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAccountConnectionRepository extends JpaRepository<ChatAccountConnection, Long> {

    @EntityGraph(attributePaths = {"user", "defaultWorkspace"})
    Optional<ChatAccountConnection> findByPlatformAndExternalUserId(
            ChatPlatform platform, String externalUserId);

    @EntityGraph(attributePaths = {"user", "defaultWorkspace"})
    Optional<ChatAccountConnection> findByPlatformAndUserId(ChatPlatform platform, Long userId);
}
