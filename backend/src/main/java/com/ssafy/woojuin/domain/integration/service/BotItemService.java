package com.ssafy.woojuin.domain.integration.service;

import com.ssafy.woojuin.domain.integration.dto.BotItemCreateRequest;
import com.ssafy.woojuin.domain.integration.entity.ChatChannelMapping;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.exception.BotAuthenticationException;
import com.ssafy.woojuin.domain.integration.exception.ChannelMappingNotFoundException;
import com.ssafy.woojuin.domain.integration.repository.ChatChannelMappingRepository;
import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.service.ItemService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BotItemService {
    private final ChatChannelMappingRepository mappingRepository;
    private final ChatCommandDeduplicationService deduplicationService;
    private final ItemService itemService;
    private final String botSecret;

    public BotItemService(ChatChannelMappingRepository mappingRepository,
            ChatCommandDeduplicationService deduplicationService,
            ItemService itemService,
            @Value("${woojuin.integrations.bot.secret:}") String botSecret) {
        this.mappingRepository = mappingRepository;
        this.deduplicationService = deduplicationService;
        this.itemService = itemService;
        this.botSecret = botSecret;
    }

    public ItemCreateResponse create(String suppliedSecret, ChatPlatform platform, String requestId,
            BotItemCreateRequest request) {
        verifySecret(suppliedSecret);
        if (request.type() == ItemType.IMAGE) {
            throw new IllegalArgumentException("이미지는 플랫폼 전용 첨부 저장 API를 사용해주세요.");
        }
        if (!deduplicationService.acquire(platform, requestId)) {
            throw new IllegalArgumentException("이미 처리된 요청입니다.");
        }
        try {
            ChatChannelMapping mapping = mappingRepository
                    .findByPlatformAndChannelId(platform, request.channelId())
                    .orElseThrow(ChannelMappingNotFoundException::new);
            return itemService.createFromRequest(
                    mapping.getWorkspace().getId(),
                    mapping.getCreatedBy().getId(),
                    new ItemCreateRequest(request.type(), request.url(), request.content()));
        } catch (RuntimeException e) {
            deduplicationService.release(platform, requestId);
            throw e;
        }
    }

    private void verifySecret(String suppliedSecret) {
        if (botSecret.isBlank() || suppliedSecret == null || !MessageDigest.isEqual(
                botSecret.getBytes(StandardCharsets.UTF_8), suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new BotAuthenticationException();
        }
    }
}
