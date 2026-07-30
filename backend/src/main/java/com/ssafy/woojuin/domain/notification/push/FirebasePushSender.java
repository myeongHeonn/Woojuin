package com.ssafy.woojuin.domain.notification.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;

/** FCM Admin SDK로 실제 발송한다. 개별 토큰 발송 실패(만료된 토큰 등)는 예외를 던지지 않고 로그만 남긴다. */
@Slf4j
public class FirebasePushSender implements PushSender {

    private final FirebaseMessaging firebaseMessaging;

    public FirebasePushSender(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public void send(String token, String title, String body) {
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .build();
        try {
            String messageId = firebaseMessaging.send(message);
            log.info("FCM 발송 성공: token={}, messageId={}", token, messageId);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 발송 실패(무시): token={}, cause={}", token, e.getMessage());
        }
    }
}
