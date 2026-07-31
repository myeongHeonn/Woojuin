package com.ssafy.woojuin.domain.notification.push;

/** 실제 푸시 발송 채널을 감춘다. Firebase 프로젝트가 준비되면 FCM 구현체를 추가한다. */
public interface PushSender {

    void send(String token, String title, String body);
}
