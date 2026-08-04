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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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

    /**
     * AGENTS.md 규칙 6: DONE 또는 PARTIAL일 때 호출된다 — FAILED는 애초에 이 이벤트가 발행되지 않는다.
     *
     * <p>알림함 저장만 한다 — 발행자(가공 반영 트랜잭션)에 참여해 아이템 상태와 함께
     * 커밋/롤백된다. FCM 발송은 {@link #pushOnItemDone}이 커밋 후에 한다: 트랜잭션 안에서
     * 보내면 (a) 외부 호출(토큰당 수백 ms) 내내 DB 커넥션을 점유하고 (b) 롤백돼도 푸시는
     * 이미 나간 뒤라 되돌릴 수 없다.
     */
    @EventListener
    @Transactional
    public void onItemDone(ItemDoneEvent event) {
        Item item = itemRepository.findById(event.itemId()).orElse(null);
        if (item == null) {
            log.warn("알림 생성할 아이템이 없음(삭제됨?): itemId={}", event.itemId());
            return;
        }

        notificationRepository.save(Notification.builder()
                .userId(item.getCreatedBy())
                .itemId(item.getId())
                .message(doneMessageOf(item))
                .build());
    }

    /**
     * 같은 이벤트의 FCM 발송 경로 — 가공 반영 트랜잭션이 <b>커밋된 뒤에만</b> 실행된다
     * (fallbackExecution: 트랜잭션 없이 발행돼도 동작, WorkspaceEventBroadcaster와 동일 계약).
     * 아이템·토큰 조회는 리포지토리의 짧은 자체 트랜잭션이라 발송 중 커넥션을 잡지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void pushOnItemDone(ItemDoneEvent event) {
        Item item = itemRepository.findById(event.itemId()).orElse(null);
        if (item == null) {
            return;   // onItemDone이 이미 경고를 남겼다
        }

        String message = doneMessageOf(item);
        List<NotificationToken> tokens = notificationTokenRepository.findByUserId(item.getCreatedBy());
        for (NotificationToken token : tokens) {
            pushSender.send(token.getToken(), "저장 완료", message);
        }
    }

    private String doneMessageOf(Item item) {
        return "\"%s\" 저장이 완료되었습니다".formatted(item.getTitle());
    }

    /** FCM 연동 수동 확인용 — 알림함에는 남기지 않고 등록된 토큰에 바로 발송한다. */
    public void sendTest(Long userId) {
        List<NotificationToken> tokens = notificationTokenRepository.findByUserId(userId);
        for (NotificationToken token : tokens) {
            pushSender.send(token.getToken(), "테스트 알림", "FCM 연동이 정상적으로 동작하고 있어요.");
        }
    }
}
