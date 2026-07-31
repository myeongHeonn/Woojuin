package com.ssafy.woojuin.domain.notification.service;

import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.event.ItemDoneEvent;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.notification.dto.NotificationResponse;
import com.ssafy.woojuin.domain.notification.entity.Notification;
import com.ssafy.woojuin.domain.notification.entity.NotificationToken;
import com.ssafy.woojuin.domain.notification.push.PushSender;
import com.ssafy.woojuin.domain.notification.repository.NotificationRepository;
import com.ssafy.woojuin.domain.notification.repository.NotificationTokenRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationTokenRepository notificationTokenRepository;
    private final ItemRepository itemRepository;
    private final PushSender pushSender;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationTokenRepository notificationTokenRepository,
                                ItemRepository itemRepository, PushSender pushSender) {
        this.notificationRepository = notificationRepository;
        this.notificationTokenRepository = notificationTokenRepository;
        this.itemRepository = itemRepository;
        this.pushSender = pushSender;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::of)
                .toList();
    }

    /** AGENTS.md 규칙 6: DONE 또는 PARTIAL일 때 호출된다 — FAILED는 애초에 이 이벤트가 발행되지 않는다. */
    @EventListener
    @Transactional
    public void onItemDone(ItemDoneEvent event) {
        Item item = itemRepository.findById(event.itemId()).orElse(null);
        if (item == null) {
            log.warn("알림 생성할 아이템이 없음(삭제됨?): itemId={}", event.itemId());
            return;
        }

        String message = "\"%s\" 저장이 완료되었습니다".formatted(item.getTitle());
        notificationRepository.save(Notification.builder()
                .userId(item.getCreatedBy())
                .itemId(item.getId())
                .message(message)
                .build());

        List<NotificationToken> tokens = notificationTokenRepository.findByUserId(item.getCreatedBy());
        for (NotificationToken token : tokens) {
            pushSender.send(token.getToken(), "저장 완료", message);
        }
    }

    /** FCM 연동 수동 확인용 — 알림함에는 남기지 않고 등록된 토큰에 바로 발송한다. */
    public void sendTest(Long userId) {
        List<NotificationToken> tokens = notificationTokenRepository.findByUserId(userId);
        for (NotificationToken token : tokens) {
            pushSender.send(token.getToken(), "테스트 알림", "FCM 연동이 정상적으로 동작하고 있어요.");
        }
    }
}
