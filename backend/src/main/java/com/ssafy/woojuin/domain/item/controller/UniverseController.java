package com.ssafy.woojuin.domain.item.controller;

import com.ssafy.woojuin.domain.item.dto.UniverseResponse;
import com.ssafy.woojuin.domain.item.service.UniverseService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 우주(3D) 뷰 조회 API. 경로는 프론트 계약(GET /workspaces/{id}/universe —
 * frontend/src/stores/mock/universe.ts의 TODO)을 따른다. 읽기 관심사별 컨트롤러 분리
 * 방식(ItemGeoController 등)과 동일하다.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class UniverseController {

    private final UniverseService universeService;
    private final CurrentUserResolver currentUserResolver;

    public UniverseController(UniverseService universeService,
            CurrentUserResolver currentUserResolver) {
        this.universeService = universeService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping("/universe")
    public ResponseEntity<ApiResponse<UniverseResponse>> universe(@PathVariable Long workspaceId) {
        Long userId = currentUserResolver.resolveUserId();
        return ResponseEntity.ok(ApiResponse.success(universeService.universe(workspaceId, userId)));
    }
}
