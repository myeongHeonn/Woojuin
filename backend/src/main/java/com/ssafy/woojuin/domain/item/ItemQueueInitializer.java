package com.ssafy.woojuin.domain.item;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 Redis Streams consumer group을 생성한다 (없으면 스트림도 함께 생성).
 * 이미 그룹이 있으면(BUSYGROUP) 정상 상황이므로 무시한다.
 */
@Slf4j
@Component
public class ItemQueueInitializer implements ApplicationRunner {

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final String consumerGroup;

    public ItemQueueInitializer(
            StringRedisTemplate redisTemplate,
            @Value("${woojuin.queue.stream-key}") String streamKey,
            @Value("${woojuin.queue.consumer-group}") String consumerGroup) {
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
        this.consumerGroup = consumerGroup;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), consumerGroup);
            log.info("Redis stream consumer group 생성 완료: stream={}, group={}", streamKey, consumerGroup);
        } catch (RedisSystemException e) {
            if (e.getMostSpecificCause() != null
                    && e.getMostSpecificCause().getMessage() != null
                    && e.getMostSpecificCause().getMessage().contains("BUSYGROUP")) {
                log.debug("Redis stream consumer group 이미 존재: stream={}, group={}", streamKey, consumerGroup);
            } else {
                throw e;
            }
        }
    }
}
