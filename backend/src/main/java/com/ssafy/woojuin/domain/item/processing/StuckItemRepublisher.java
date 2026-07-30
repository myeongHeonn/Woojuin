package com.ssafy.woojuin.domain.item.processing;

import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.item.service.ItemQueueProducer;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PROCESSING에 오래 머문 아이템을 큐에 다시 발행하는 안전망.
 *
 * <p>{@link PendingMessageReclaimer}는 <b>배달된 적 있는</b> pending 메시지만 구한다.
 * 큐에 메시지가 아예 없는 아이템은 어느 쪽도 못 구하고 영원히 PROCESSING에 남는데,
 * 그런 구멍은 실제로 생긴다 —
 * <ul>
 *   <li>DB 저장은 커밋됐는데 발행이 실패(그 순간 Redis 장애)</li>
 *   <li>컨슈머가 죽어 있던 사이의 유실(재배포 등), Redis 데이터 소실</li>
 * </ul>
 *
 * <p><b>중복 발행은 무해하다</b> — 메시지가 이미 큐에 있는 아이템에 또 발행돼도,
 * 먼저 처리된 쪽이 상태를 확정하고 나중 메시지는 프로세서의 {@code status != PROCESSING}
 * 가드에서 스킵된 뒤 ACK된다. 대가는 DB 조회 한 번이다.
 *
 * <p><b>기준 시간은 회수기의 재시도 수명보다 길어야 한다.</b> 처리가 계속 실패하는
 * 아이템은 회수기가 최대 배달 횟수(3회 × idle 3분 ≈ 10분)를 채우고 FAILED로 확정하는
 * 중인데, 그보다 먼저 재발행하면 포기 직전의 아이템을 새 메시지로 되살려 AI 호출을
 * 계속 태우는 루프가 된다. 기본 15분은 그 수명이 끝나길 기다리는 값이다.
 *
 * <p>다중 인스턴스에서 여러 스케줄러가 겹쳐 돌아도 중복 발행이 무해하므로 락이 필요 없다.
 */
@Slf4j
@Component
public class StuckItemRepublisher {

    private final ItemRepository itemRepository;
    private final ItemQueueProducer itemQueueProducer;
    private final long stuckAfterMs;
    private final int batchSize;

    public StuckItemRepublisher(
            ItemRepository itemRepository,
            ItemQueueProducer itemQueueProducer,
            @Value("${woojuin.queue.republish-after-ms:900000}") long stuckAfterMs,
            @Value("${woojuin.queue.republish-batch-size:50}") int batchSize) {
        this.itemRepository = itemRepository;
        this.itemQueueProducer = itemQueueProducer;
        this.stuckAfterMs = stuckAfterMs;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${woojuin.queue.republish-interval-ms:300000}")
    public void republish() {
        List<Item> stuck;
        try {
            OffsetDateTime cutoff = OffsetDateTime.now().minus(Duration.ofMillis(stuckAfterMs));
            stuck = itemRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                    ItemStatus.PROCESSING, cutoff, Limit.of(batchSize));
        } catch (Exception e) {
            // DB 일시 장애 — 다음 주기에 다시 시도한다.
            log.debug("재발행 대상 조회 실패, 다음 주기 재시도: {}", e.toString());
            return;
        }
        if (stuck.isEmpty()) {
            return;
        }

        // 여기 잡힌다는 건 발행 유실이든 컨슈머 정지든 어딘가가 이미 새었다는 뜻이라 warn.
        log.warn("PROCESSING에 {}ms 이상 머문 아이템 {}건 재발행", stuckAfterMs, stuck.size());
        for (Item item : stuck) {
            try {
                itemQueueProducer.publish(item.getId(), item.getWorkspaceId(), item.getType());
            } catch (Exception e) {
                // 발행이 또 실패해도 아이템은 PROCESSING 그대로라 다음 주기에 다시 잡힌다.
                log.warn("재발행 실패(다음 주기 재시도): itemId={}, cause={}", item.getId(), e.toString());
                return;   // Redis가 죽은 상황이면 나머지도 실패할 테니 이번 주기는 접는다
            }
        }
    }
}
