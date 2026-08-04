package com.ssafy.woojuin.domain.integration.service;

import com.ssafy.woojuin.domain.integration.dto.LinkCodeResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatLinkCodeService {

    private static final String KEY_PREFIX = "woojuin:chat-link:";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int CODE_LENGTH = 6;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public ChatLinkCodeService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public LinkCodeResponse issue(Long userId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = generateCode();
            Boolean stored = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + code, userId.toString(), TTL);
            if (Boolean.TRUE.equals(stored)) {
                return new LinkCodeResponse(code, OffsetDateTime.now().plus(TTL));
            }
        }
        throw new IllegalStateException("연결 코드를 생성하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }

    public Long consume(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        String value = redisTemplate.opsForValue()
                .getAndDelete(KEY_PREFIX + rawCode.trim().toUpperCase());
        return value == null ? null : Long.valueOf(value);
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
