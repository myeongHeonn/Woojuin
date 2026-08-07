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

    /**
     * 연동 해제 시 그 플랫폼에서 본인이 만든 매핑을 정리한다 — 봇이 매핑 생성자 명의로
     * 저장하므로(BotItemService) 남겨 두면 끊은 사람 명의로 계속 저장된다.
     */
    void deleteByPlatformAndCreatedById(ChatPlatform platform, Long createdById);
}
