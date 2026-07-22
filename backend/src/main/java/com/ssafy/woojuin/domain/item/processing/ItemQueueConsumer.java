package com.ssafy.woojuin.domain.item.processing;

import com.ssafy.woojuin.domain.item.ItemType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.stereotype.Component;

/**
 * Redis Streams(woojuin:item-processing) consumer group의 유일한 소비자.
 * 메시지 type을 보고 맞는 {@link ItemProcessor}에 위임한 뒤 ACK한다.
 *
 * <p><b>왜 컨슈머가 하나인가</b> — {@link ItemProcessor} javadoc 참조. 타입별로 컨슈머를
 * 나누면 consumer group의 임의 분배 때문에 URL 메시지가 이미지 워커에게 갈 수 있다.
 *
 * <p><b>ACK 규칙</b>
 * <ul>
 *   <li>정상 처리 → ACK</li>
 *   <li>파싱 불가한 독약 메시지 → 재시도해도 소용없으므로 ACK하고 버린다</li>
 *   <li>담당 프로세서가 아직 없는 타입 → ACK하지 않는다(pending 유지). 해당 묶음의
 *       프로세서가 배포되면 재기동 시 pending을 다시 집어 처리한다</li>
 *   <li>처리 중 예외 → ACK하지 않는다(pending 유지). 재시도 대상. 상한/에스컬레이션은 후속 단계</li>
 * </ul>
 */
@Slf4j
@Component
public class ItemQueueConsumer
        implements StreamListener<String, MapRecord<String, String, String>>,
        InitializingBean, DisposableBean {

    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redisTemplate;
    private final List<ItemProcessor> processors;
    private final String streamKey;
    private final String consumerGroup;
    private final String consumerName;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    public ItemQueueConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            List<ItemProcessor> processors,
            @Value("${woojuin.queue.stream-key}") String streamKey,
            @Value("${woojuin.queue.consumer-group}") String consumerGroup) {
        this.connectionFactory = connectionFactory;
        this.redisTemplate = redisTemplate;
        this.processors = processors;
        this.streamKey = streamKey;
        this.consumerGroup = consumerGroup;
        // 인스턴스마다 고유해야 pending 추적이 섞이지 않는다. 다중 인스턴스 배포 대비.
        this.consumerName = "consumer-" + UUID.randomUUID().toString().substring(0, 8);
        verifyNoDuplicateProcessors(processors);
    }

    /** 한 타입에 프로세서가 둘이면 어느 쪽이 처리할지 모호하므로 기동을 막는다. */
    private void verifyNoDuplicateProcessors(List<ItemProcessor> processors) {
        for (ItemType type : ItemType.values()) {
            long count = processors.stream().filter(p -> p.supports(type)).count();
            if (count > 1) {
                throw new IllegalStateException(
                        "ItemProcessor가 타입 " + type + "에 " + count + "개 등록됨 — 타입당 하나여야 함");
            }
        }
    }

    @Override
    public void afterPropertiesSet() {
        var options = StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(2))
                .build();
        this.container = StreamMessageListenerContainer.create(connectionFactory, options);
        // ReadOffset.lastConsumed() = 이 그룹이 아직 배달받지 않은 새 메시지("&")부터.
        // 기존 pending은 재기동 시 컨테이너가 자동으로 다시 배달하지 않으므로, pending
        // 복구(XAUTOCLAIM)는 후속 단계에서 별도 스케줄러로 처리한다.
        container.receive(
                Consumer.from(consumerGroup, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this);
        container.start();
        log.info("아이템 큐 컨슈머 시작: stream={}, group={}, consumer={}",
                streamKey, consumerGroup, consumerName);
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        ItemProcessingMessage message;
        try {
            message = ItemProcessingMessage.from(record.getValue());
        } catch (IllegalArgumentException e) {
            // 독약 메시지 — 형식이 깨져 재시도해도 영원히 실패. ACK하고 버린다.
            log.error("큐 메시지 파싱 실패, 폐기: recordId={}, value={}",
                    record.getId(), record.getValue(), e);
            acknowledge(record);
            return;
        }

        ItemProcessor processor = processors.stream()
                .filter(p -> p.supports(message.type()))
                .findFirst()
                .orElse(null);
        if (processor == null) {
            // 담당 묶음이 아직 프로세서를 안 붙임. ACK하지 않고 pending에 남겨 나중에 처리.
            log.warn("타입 {}를 처리할 프로세서 없음, pending 유지: itemId={}",
                    message.type(), message.itemId());
            return;
        }

        try {
            processor.process(message);
            acknowledge(record);
        } catch (Exception e) {
            // pending에 남아 재시도된다. 무한 재시도 방지 상한은 후속 단계에서.
            log.error("아이템 처리 실패, 재시도 예정: itemId={}, type={}",
                    message.itemId(), message.type(), e);
        }
    }

    private void acknowledge(MapRecord<String, String, String> record) {
        redisTemplate.opsForStream().acknowledge(consumerGroup, record);
    }

    @Override
    public void destroy() {
        if (container != null) {
            container.stop();
            log.info("아이템 큐 컨슈머 종료: consumer={}", consumerName);
        }
    }
}
