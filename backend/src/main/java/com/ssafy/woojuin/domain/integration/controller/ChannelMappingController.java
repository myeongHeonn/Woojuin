package com.ssafy.woojuin.domain.integration.controller;

import com.ssafy.woojuin.domain.integration.dto.ChannelMappingRequest;
import com.ssafy.woojuin.domain.integration.dto.ChannelMappingResponse;
import com.ssafy.woojuin.domain.integration.service.ChannelMappingService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/channel-mappings")
public class ChannelMappingController {
    private final ChannelMappingService service;
    private final CurrentUserResolver currentUserResolver;

    public ChannelMappingController(ChannelMappingService service, CurrentUserResolver currentUserResolver) {
        this.service = service;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChannelMappingResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(currentUserResolver.resolveUserId())));
    }

    @AuthenticatedUser
    @PostMapping
    public ResponseEntity<ApiResponse<ChannelMappingResponse>> create(@Valid @RequestBody ChannelMappingRequest request) {
        ChannelMappingResponse response = service.create(currentUserResolver.resolveUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(201, "success", response));
    }
}
