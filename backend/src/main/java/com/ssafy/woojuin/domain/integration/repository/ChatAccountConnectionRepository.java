package com.ssafy.woojuin.domain.integration.repository;

import com.ssafy.woojuin.domain.integration.entity.ChatAccountConnection;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAccountConnectionRepository extends JpaRepository<ChatAccountConnection, Long> {

    @EntityGraph(attributePaths = {"user", "defaultWorkspace"})
    Optional<ChatAccountConnection> findByPlatformAndExternalUserId(
            ChatPlatform platform, String externalUserId);

    @EntityGraph(attributePaths = {"user", "defaultWorkspace"})
    Optional<ChatAccountConnection> findByPlatformAndUserId(ChatPlatform platform, Long userId);

    /** 연동 목록 화면용 — 기본 워크스페이스 이름까지 한 번에 가져온다(N+1 방지) */
    @EntityGraph(attributePaths = {"defaultWorkspace"})
    List<ChatAccountConnection> findAllByUserIdOrderByCreatedAtAsc(Long userId);

    /** 해제용 — 본인 소유 확인을 조회 조건에 넣어 남의 id 는 "없음"과 구분되지 않게 한다 */
    Optional<ChatAccountConnection> findByIdAndUserId(Long id, Long userId);
}
