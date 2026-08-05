package com.ssafy.woojuin.domain.integration.service;

import com.ssafy.woojuin.domain.integration.dto.ChatConnectionResponse;
import com.ssafy.woojuin.domain.integration.entity.ChatAccountConnection;
import com.ssafy.woojuin.domain.integration.repository.ChatAccountConnectionRepository;
import com.ssafy.woojuin.domain.integration.repository.ChatChannelMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 내 채팅 연동(Discord·Mattermost)의 조회와 해제 — 기기·앱 관리 화면의 "연결된 앱" 뒤편.
 */
@Service
public class ChatConnectionService {

    private final ChatAccountConnectionRepository connectionRepository;
    private final ChatChannelMappingRepository mappingRepository;

    public ChatConnectionService(ChatAccountConnectionRepository connectionRepository,
                                 ChatChannelMappingRepository mappingRepository) {
        this.connectionRepository = connectionRepository;
        this.mappingRepository = mappingRepository;
    }

    @Transactional(readOnly = true)
    public List<ChatConnectionResponse> list(Long userId) {
        return connectionRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(ChatConnectionResponse::from)
                .toList();
    }

    /**
     * 연동을 끊는다.
     *
     * 본인 소유 확인을 조회 조건에 넣는다 — 남의 id 로 시도하면 "없는 연동"과 같은 응답이라
     * 존재 여부도 새어 나가지 않는다.
     *
     * **행을 지운다(하드 삭제).** (platform, externalUserId) 유니크 제약이 있어 행이 남으면
     * 같은 계정으로 다시 연결할 때 막힌다. 지우면 Mattermost OAuth 토큰도 행과 함께 사라져
     * "해제 후 토큰이 남지 않는다"가 저절로 성립한다.
     *
     * **그 플랫폼에서 본인이 만든 채널 매핑도 같이 지운다.** 봇은 저장할 때 매핑 생성자 명의로
     * 아이템을 만든다(BotItemService — 연결 여부를 보지 않는다). 매핑을 남겨 두면 연동을 끊은
     * 사람 명의로 그 채널이 계속 저장하는 오작동이 된다. 채널당 매핑이 하나뿐이라
     * (platform, channel_id 유니크) 지워도 다른 사용자가 다시 매핑하면 그만이다.
     */
    @Transactional
    public void disconnect(Long userId, Long connectionId) {
        ChatAccountConnection connection = connectionRepository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("연동을 찾을 수 없습니다"));

        mappingRepository.deleteByPlatformAndCreatedById(connection.getPlatform(), userId);
        connectionRepository.delete(connection);
    }
}
