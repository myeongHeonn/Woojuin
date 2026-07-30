package com.ssafy.woojuin.domain.item.processing;

import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.global.common.Timing;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisSystemException;
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
 * Redis Streams(woojuin:item-processing) consumer group의 소비자들.
 * 새 메시지를 받아 {@link ItemProcessingDispatcher}에 넘기고, 결과에 따라 ACK를 정한다.
 *
 * <p><b>동일한 컨슈머 N개</b>(consumer-count, 기본 3)를 같은 그룹에 등록해 병렬로 소비한다.
 * 아이템 하나가 크롤링·LLM 호출로 수십 초를 먹는 순차 처리로는 동시 사용자 몇 명만으로도
 * 대기열이 분 단위로 밀리기 때문이다. <b>타입별로 컨슈머를 나누지 않는 이유</b>는
 * {@link ItemProcessor} javadoc 참조 — consumer group의 임의 분배 때문에 URL 메시지가
 * 이미지 전용 워커에게 갈 수 있어, 모든 컨슈머가 동일하게 디스패처로 분기한다.
 *
 * <p>개수를 늘릴 땐 <b>DB 커넥션 풀부터 볼 것</b> — 프로세서가 @Transactional이라 컨슈머
 * 하나가 크롤·LLM 호출 내내 커넥션 하나를 점유한다. 풀 기본값 10에서 컨슈머 3 + 회수기 1이면
 * 장기 점유가 최대 4, 나머지가 웹 요청 몫이다.
 *
 * <p>이 컨슈머들은 <b>새 메시지(never-delivered)만</b> 처리한다. 처리에 실패해 pending에
 * 남은 메시지의 재시도·최종 포기는 {@link PendingMessageReclaimer}가 맡는다.
 */
@Slf4j
@Component
public class ItemQueueConsumer
        implements StreamListener<String, MapRecord<String, String, String>>,
        InitializingBean, DisposableBean {

    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redisTemplate;
    private final ItemProcessingDispatcher dispatcher;
    private final String streamKey;
    private final String consumerGroup;
    private final String consumerNamePrefix;
    private final int consumerCount;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    public ItemQueueConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            ItemProcessingDispatcher dispatcher,
            List<ItemProcessor> processors,
            @Value("${woojuin.queue.stream-key}") String streamKey,
            @Value("${woojuin.queue.consumer-group}") String consumerGroup,
            @Value("${woojuin.queue.consumer-count:3}") int consumerCount) {
        this.connectionFactory = connectionFactory;
        this.redisTemplate = redisTemplate;
        this.dispatcher = dispatcher;
        this.streamKey = streamKey;
        this.consumerGroup = consumerGroup;
        // 인스턴스마다 고유해야 pending 추적이 섞이지 않는다. 다중 인스턴스 배포 대비.
        this.consumerNamePrefix = "consumer-" + UUID.randomUUID().toString().substring(0, 8);
        this.consumerCount = consumerCount;
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
        // 컨테이너를 켜기 전에 consumer group을 보장한다. 그룹이 없는 상태로 XREADGROUP을
        // 시작하면 NOGROUP 에러로 컨테이너가 영구 정지하기 때문이다(신선한 Redis 배포 시).
        createGroupIfAbsent();

        var options = StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(2))
                .build();
        this.container = StreamMessageListenerContainer.create(connectionFactory, options);
        // ReadOffset.lastConsumed() = 이 그룹이 아직 배달받지 않은 새 메시지(">")부터.
        // 기존 pending은 컨테이너가 자동으로 다시 배달하지 않으므로 PendingMessageReclaimer가 회수한다.
        // 이름이 다른 컨슈머 N개를 등록하면 각각 폴링 스레드를 갖고, Redis가 새 메시지를
        // 그중 노는 컨슈머에게 분배해 병렬 처리가 된다.
        for (int i = 1; i <= consumerCount; i++) {
            container.receive(
                    Consumer.from(consumerGroup, consumerNamePrefix + "-" + i),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                    this);
        }
        container.start();
        log.info("아이템 큐 컨슈머 시작: stream={}, group={}, consumerPrefix={}, count={}",
                streamKey, consumerGroup, consumerNamePrefix, consumerCount);
    }

    /**
     * consumer group을 생성한다(없으면 스트림도 함께 — createGroup은 MKSTREAM 동작).
     * 이미 있으면(BUSYGROUP) 정상이므로 무시한다.
     */
    private void createGroupIfAbsent() {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), consumerGroup);
            log.info("Redis stream consumer group 생성: stream={}, group={}", streamKey, consumerGroup);
        } catch (RedisSystemException e) {
            Throwable cause = e.getMostSpecificCause();
            if (cause != null && cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                log.debug("consumer group 이미 존재: group={}", consumerGroup);
            } else {
                throw e;
            }
        }
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        long dispatchStarted = Timing.start();
        long queueWaitMs = queueWaitMillis(record);
        ItemProcessingDispatcher.Outcome outcome = dispatcher.handle(record.getValue());
        log.info("pipeline_timing itemId={} type={} stage=queue_consume "
                        + "queueWaitMs={} dispatchMs={} outcome={}",
                record.getValue().get("itemId"), record.getValue().get("type"),
                queueWaitMs, Timing.elapsedMillis(dispatchStarted), outcome);
        // PROCESSED/POISON은 ACK로 큐에서 제거. NO_PROCESSOR/RETRYABLE은 pending에 남겨
        // PendingMessageReclaimer가 이어받게 한다.
        if (outcome == ItemProcessingDispatcher.Outcome.PROCESSED
                || outcome == ItemProcessingDispatcher.Outcome.POISON) {
            redisTemplate.opsForStream().acknowledge(consumerGroup, record);
        }
    }

    /**
     * Redis Stream ID의 앞부분은 서버가 레코드를 생성한 epoch millis다. 기존 메시지 형식을
     * 바꾸지 않고도 발행→소비 대기 시간을 계산할 수 있다. 파싱할 수 없는 커스텀 ID면 -1.
     */
    private long queueWaitMillis(MapRecord<String, String, String> record) {
        String recordId = record.getId().getValue();
        int separator = recordId.indexOf('-');
        String epochMillis = separator >= 0 ? recordId.substring(0, separator) : recordId;
        try {
            return Math.max(0, System.currentTimeMillis() - Long.parseLong(epochMillis));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public void destroy() {
        if (container != null) {
            container.stop();
            log.info("아이템 큐 컨슈머 종료: consumerPrefix={}", consumerNamePrefix);
        }
    }
}
