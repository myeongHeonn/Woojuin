package com.ssafy.woojuin.domain.item.processing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.item.service.ItemQueueProducer;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StuckItemRepublisherTest {

    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final ItemQueueProducer itemQueueProducer = mock(ItemQueueProducer.class);

    private final StuckItemRepublisher republisher =
            new StuckItemRepublisher(itemRepository, itemQueueProducer, 900_000L, 50);

    private Item stuckItem(Long id, Long workspaceId, ItemType type) {
        Item item = Item.builder().workspaceId(workspaceId).createdBy(1L).type(type)
                .url(type == ItemType.URL ? "https://example.com" : null)
                .content(type == ItemType.MEMO ? "메모" : null)
                .build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    void 오래_머문_PROCESSING_아이템을_다시_발행한다() {
        when(itemRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(ItemStatus.PROCESSING), any(), any()))
                .thenReturn(List.of(stuckItem(1L, 10L, ItemType.URL), stuckItem(2L, 20L, ItemType.MEMO)));

        republisher.republish();

        verify(itemQueueProducer).publish(1L, 10L, ItemType.URL);
        verify(itemQueueProducer).publish(2L, 20L, ItemType.MEMO);
    }

    @Test
    void 대상이_없으면_발행하지_않는다() {
        when(itemRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of());

        republisher.republish();

        verify(itemQueueProducer, never()).publish(any(), any(), any());
    }

    @Test
    void 조회가_실패해도_예외없이_다음_주기로_넘어간다() {
        when(itemRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(any(), any(), any()))
                .thenThrow(new RuntimeException("DB 일시 장애"));

        republisher.republish();   // 예외 없이 통과

        verify(itemQueueProducer, never()).publish(any(), any(), any());
    }

    @Test
    void 발행이_실패하면_이번_주기를_접는다() {
        when(itemRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(List.of(stuckItem(1L, 10L, ItemType.URL), stuckItem(2L, 20L, ItemType.MEMO)));
        doThrow(new RuntimeException("Redis 장애")).when(itemQueueProducer).publish(1L, 10L, ItemType.URL);

        republisher.republish();   // 예외 없이 통과

        verify(itemQueueProducer, never()).publish(eq(2L), any(), any());
    }
}
