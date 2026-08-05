package com.ssafy.woojuin.domain.integration.controller;

import com.ssafy.woojuin.domain.integration.dto.ChatConnectionResponse;
import com.ssafy.woojuin.domain.integration.service.ChatConnectionService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 내 채팅 연동 조회·해제 — 기기·앱 관리 화면(S15P11C105-460)의 "연결된 앱" 섹션이 쓴다.
 * 연결하는 길(link-code·OAuth)은 ChatIntegrationController 에 이미 있다.
 */
@RestController
@RequestMapping("/api/integrations/connections")
public class ChatConnectionController {

    private final ChatConnectionService connectionService;
    private final CurrentUserResolver currentUserResolver;

    public ChatConnectionController(ChatConnectionService connectionService,
                                    CurrentUserResolver currentUserResolver) {
        this.connectionService = connectionService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatConnectionResponse>>> list() {
        Long userId = currentUserResolver.resolveUserId();
        return ResponseEntity.ok(ApiResponse.success(connectionService.list(userId)));
    }

    @AuthenticatedUser
    @DeleteMapping("/{connectionId}")
    public ResponseEntity<ApiResponse<Void>> disconnect(@PathVariable Long connectionId) {
        Long userId = currentUserResolver.resolveUserId();
        connectionService.disconnect(userId, connectionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
