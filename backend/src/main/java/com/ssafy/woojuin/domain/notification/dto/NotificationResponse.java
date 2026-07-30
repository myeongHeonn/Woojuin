package com.ssafy.woojuin.domain.notification.dto;

import com.ssafy.woojuin.domain.notification.entity.Notification;
import java.time.OffsetDateTime;

public record NotificationResponse(Long id, Long itemId, String message, OffsetDateTime createdAt) {

    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getItemId(), notification.getMessage(), notification.getCreatedAt());
    }
}
