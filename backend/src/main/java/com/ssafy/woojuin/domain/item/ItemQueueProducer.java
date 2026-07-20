package com.ssafy.woojuin.domain.item;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 저장 직후 아이템을 Redis Streams(woojuin:item-processing)에 적재한다.
 * 실제 소비(트랙 A/B AI 가공)는 이 번들(묶음 C) 범위 밖 — 묶음 D/F 담당.
 */
@Component
public class ItemQueueProducer {

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;

    public ItemQueueProducer(
            StringRedisTemplate redisTemplate,
            @Value("${woojuin.queue.stream-key}") String streamKey) {
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
    }

    public void publish(Long itemId, Long workspaceId, ItemType type) {
        redisTemplate.opsForStream().add(streamKey, Map.of(
                "itemId", String.valueOf(itemId),
                "workspaceId", String.valueOf(workspaceId),
                "type", type.name()));
    }
}
