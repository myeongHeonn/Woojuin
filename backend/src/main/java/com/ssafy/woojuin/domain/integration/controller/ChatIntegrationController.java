package com.ssafy.woojuin.domain.integration.controller;

import com.ssafy.woojuin.domain.integration.dto.LinkCodeResponse;
import com.ssafy.woojuin.domain.integration.service.ChatLinkCodeService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/chat")
public class ChatIntegrationController {

    private final ChatLinkCodeService linkCodeService;
    private final CurrentUserResolver currentUserResolver;

    public ChatIntegrationController(
            ChatLinkCodeService linkCodeService,
            CurrentUserResolver currentUserResolver) {
        this.linkCodeService = linkCodeService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @PostMapping("/link-code")
    public ResponseEntity<ApiResponse<LinkCodeResponse>> issueLinkCode() {
        Long userId = currentUserResolver.resolveUserId();
        return ResponseEntity.ok(ApiResponse.success(linkCodeService.issue(userId)));
    }
}
