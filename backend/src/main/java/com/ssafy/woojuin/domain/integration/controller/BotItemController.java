package com.ssafy.woojuin.domain.integration.controller;

import com.ssafy.woojuin.domain.integration.dto.BotItemCreateRequest;
import com.ssafy.woojuin.domain.integration.entity.ChatPlatform;
import com.ssafy.woojuin.domain.integration.service.BotItemService;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.global.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/bot/items")
public class BotItemController {
    private final BotItemService service;

    public BotItemController(BotItemService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ItemCreateResponse>> create(
            @RequestHeader("X-Bot-Secret") String secret,
            @RequestHeader("X-Bot-Platform") ChatPlatform platform,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody BotItemCreateRequest request) {
        ItemCreateResponse response = service.create(secret, platform, requestId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(201, "success", response));
    }
}
