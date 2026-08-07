package com.ssafy.woojuin.domain.integration.service;

import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatCommandDeduplicationService {

    private static final String KEY_PREFIX = "woojuin:chat-command:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public ChatCommandDeduplicationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquire(ChatPlatform platform, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return true;
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + platform.name().toLowerCase() + ":" + requestId, "1", TTL);
        return Boolean.TRUE.equals(acquired);
    }

    public void release(ChatPlatform platform, String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            redisTemplate.delete(KEY_PREFIX + platform.name().toLowerCase() + ":" + requestId);
        }
    }
}
