package com.ssafy.woojuin.domain.notification.controller;

import com.ssafy.woojuin.domain.notification.dto.DeleteTokenRequest;
import com.ssafy.woojuin.domain.notification.dto.RegisterTokenRequest;
import com.ssafy.woojuin.domain.notification.service.NotificationTokenService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/tokens")
public class NotificationTokenController {

    private final NotificationTokenService notificationTokenService;
    private final CurrentUserResolver currentUserResolver;

    public NotificationTokenController(NotificationTokenService notificationTokenService,
                                        CurrentUserResolver currentUserResolver) {
        this.notificationTokenService = notificationTokenService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody RegisterTokenRequest request) {
        Long userId = currentUserResolver.resolveUserId();
        notificationTokenService.register(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(201, "success", null));
    }

    @AuthenticatedUser
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(@RequestBody DeleteTokenRequest request) {
        Long userId = currentUserResolver.resolveUserId();
        notificationTokenService.delete(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
