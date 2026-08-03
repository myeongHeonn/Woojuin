package com.ssafy.woojuin.domain.integration.repository;

import com.ssafy.woojuin.domain.integration.entity.ChatChannelMapping;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatChannelMappingRepository extends JpaRepository<ChatChannelMapping, Long> {
    @EntityGraph(attributePaths = {"workspace", "createdBy"})
    Optional<ChatChannelMapping> findByPlatformAndChannelId(ChatPlatform platform, String channelId);

    @EntityGraph(attributePaths = {"workspace"})
    List<ChatChannelMapping> findByCreatedByIdOrderByIdAsc(Long userId);
}
