package com.ssafy.woojuin.domain.notification.exception;

public class NotificationTokenNotFoundException extends RuntimeException {

    public NotificationTokenNotFoundException(String token) {
        super("COMMON_404: 알림 토큰을 찾을 수 없습니다 (token=" + token + ")");
    }
}
