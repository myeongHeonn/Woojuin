package com.ssafy.woojuin.domain.notification.controller;

import com.ssafy.woojuin.domain.notification.dto.NotificationResponse;
import com.ssafy.woojuin.domain.notification.service.NotificationService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserResolver currentUserResolver;

    public NotificationController(NotificationService notificationService, CurrentUserResolver currentUserResolver) {
        this.notificationService = notificationService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list() {
        Long userId = currentUserResolver.resolveUserId();
        return ResponseEntity.ok(ApiResponse.success(notificationService.list(userId)));
    }

    /** FCM 연동 수동 확인용 — 등록된 토큰에 테스트 푸시를 즉시 발송한다. */
    @AuthenticatedUser
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Void>> sendTest() {
        Long userId = currentUserResolver.resolveUserId();
        notificationService.sendTest(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
