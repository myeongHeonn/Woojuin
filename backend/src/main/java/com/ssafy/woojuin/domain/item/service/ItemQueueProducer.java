package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.item.entity.ItemType;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 저장 직후 아이템을 Redis Streams(woojuin:item-processing)에 적재한다.
 * 실제 소비(트랙 A/B AI 가공)는 이 번들(묶음 C) 범위 밖 — 묶음 D/F 담당.
 */
@Slf4j
@Component
public class ItemQueueProducer {

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final long maxLength;

    public ItemQueueProducer(
            StringRedisTemplate redisTemplate,
            @Value("${woojuin.queue.stream-key}") String streamKey,
            @Value("${woojuin.queue.max-length:10000}") long maxLength) {
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
        this.maxLength = maxLength;
    }

    /**
     * 트랜잭션 안에서 호출되면 커밋 이후로 발행을 미룬다. 커밋 전에 발행하면 컨슈머가
     * 메시지를 먼저 받아 아직 커밋되지 않은 아이템을 조회해 실패할 수 있고, 롤백 시에는
     * 존재하지 않을 아이템을 가리키는 고아 메시지가 남는다.
     */
    public void publish(Long itemId, Long workspaceId, ItemType type) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(itemId, workspaceId, type);
                }
            });
            return;
        }
        doPublish(itemId, workspaceId, type);
    }

    private void doPublish(Long itemId, Long workspaceId, ItemType type) {
        StreamOperations<String, String, String> stream = redisTemplate.opsForStream();
        stream.add(streamKey, Map.of(
                "itemId", String.valueOf(itemId),
                "workspaceId", String.valueOf(workspaceId),
                "type", type.name()));
        try {
            // ACK는 PEL에서만 제거하므로, 처리 완료 원본이 무한히 쌓이지 않게 근사 길이로 정리한다.
            stream.trim(streamKey, maxLength, true);
        } catch (RuntimeException e) {
            // XADD가 성공한 뒤 정리만 실패한 경우 발행 실패로 오인해 같은 메시지를 재발행하지 않는다.
            log.warn("Redis Stream 길이 정리 실패(메시지 발행은 완료): streamKey={}, cause={}",
                    streamKey, e.toString());
        }
    }
}
