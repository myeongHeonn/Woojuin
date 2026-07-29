package com.ssafy.woojuin.domain.notification.service;

import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.event.ItemDoneEvent;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.notification.dto.NotificationResponse;
import com.ssafy.woojuin.domain.notification.entity.Notification;
import com.ssafy.woojuin.domain.notification.entity.NotificationToken;
import com.ssafy.woojuin.domain.notification.push.PushSender;
import com.ssafy.woojuin.domain.notification.repository.NotificationRepository;
import com.ssafy.woojuin.domain.notification.repository.NotificationTokenRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationTokenRepository notificationTokenRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private PushSender pushSender;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("목록 조회: 최신순으로 응답을 매핑한다")
    void list_returnsResponses() {
        Notification notification = Notification.builder()
                .userId(1L).itemId(10L).message("\"제목\" 저장이 완료되었습니다").build();
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(notification));

        List<NotificationResponse> result = notificationService.list(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).itemId()).isEqualTo(10L);
        assertThat(result.get(0).message()).isEqualTo("\"제목\" 저장이 완료되었습니다");
    }

    @Test
    @DisplayName("아이템 DONE 이벤트: 알림을 저장하고, 소유자의 모든 등록 토큰에 발송한다")
    void onItemDone_savesNotificationAndPushesToAllTokens() {
        Item item = itemOf(10L, 1L, "제목");
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        NotificationToken tokenA = NotificationToken.builder().userId(1L).token("token-a").build();
        NotificationToken tokenB = NotificationToken.builder().userId(1L).token("token-b").build();
        when(notificationTokenRepository.findByUserId(1L)).thenReturn(List.of(tokenA, tokenB));

        notificationService.onItemDone(new ItemDoneEvent(10L));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getItemId()).isEqualTo(10L);

        verify(pushSender).send(eq("token-a"), any(), any());
        verify(pushSender).send(eq("token-b"), any(), any());
    }

    @Test
    @DisplayName("아이템 DONE 이벤트: 삭제된 아이템이면 조용히 무시한다")
    void onItemDone_missingItem_doesNothing() {
        when(itemRepository.findById(10L)).thenReturn(Optional.empty());

        notificationService.onItemDone(new ItemDoneEvent(10L));

        verify(notificationRepository, never()).save(any());
        verify(pushSender, never()).send(any(), any(), any());
    }

    private Item itemOf(Long id, Long createdBy, String title) {
        Item item = Item.builder()
                .workspaceId(100L).createdBy(createdBy).type(ItemType.MEMO).title(title).build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
