package com.ssafy.woojuin.domain.auth.controller;

import com.ssafy.woojuin.domain.auth.dto.SessionResponse;
import com.ssafy.woojuin.domain.auth.service.UserSessionService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 로그인된 기기(세션) 관리 — 기기·앱 관리 화면(S15P11C105-460)의 "연결된 기기" 섹션이 쓴다.
 */
@RestController
@RequestMapping("/api/auth/sessions")
public class SessionController {

    private final UserSessionService sessionService;
    private final CurrentUserResolver currentUserResolver;

    public SessionController(UserSessionService sessionService, CurrentUserResolver currentUserResolver) {
        this.sessionService = sessionService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping
    public ApiResponse<List<SessionResponse>> list() {
        return ApiResponse.success(sessionService.list(
                currentUserResolver.resolveUserId(), currentUserResolver.resolveSessionId()));
    }

    /** 특정 기기 해제. 지금 쓰는 기기의 sid 를 넣으면 로그아웃과 같다 — 클라이언트가 로그인 화면으로 간다 */
    @AuthenticatedUser
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> revoke(@PathVariable String sessionId) {
        sessionService.revoke(currentUserResolver.resolveUserId(), sessionId);
        return ApiResponse.success(null);
    }

    /** 모든 기기에서 로그아웃 — 이 기기까지 끊기므로 응답을 받은 클라이언트는 로그인 화면으로 간다 */
    @AuthenticatedUser
    @DeleteMapping
    public ApiResponse<Void> revokeAll() {
        sessionService.revokeAll(currentUserResolver.resolveUserId());
        return ApiResponse.success(null);
    }
}
