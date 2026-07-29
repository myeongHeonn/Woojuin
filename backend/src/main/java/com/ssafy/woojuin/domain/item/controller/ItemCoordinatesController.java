package com.ssafy.woojuin.domain.item.controller;

import com.ssafy.woojuin.domain.item.dto.ItemCoordinateResponse;
import com.ssafy.woojuin.domain.item.service.ItemCoordinatesService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 우주(3D) 뷰 조회 API. 읽기 관심사별 컨트롤러 분리 방식(ItemGeoController 등)과 동일하다.
 *
 * <p>{@code data}는 페이지네이션 없는 평면 배열 — 우주 뷰는 모든 별을 한 번에 그린다.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class ItemCoordinatesController {

    private final ItemCoordinatesService itemCoordinatesService;
    private final CurrentUserResolver currentUserResolver;

    public ItemCoordinatesController(ItemCoordinatesService itemCoordinatesService,
            CurrentUserResolver currentUserResolver) {
        this.itemCoordinatesService = itemCoordinatesService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping("/items/coordinates")
    public ResponseEntity<ApiResponse<List<ItemCoordinateResponse>>> coordinates(
            @PathVariable Long workspaceId) {
        Long userId = currentUserResolver.resolveUserId();
        return ResponseEntity.ok(
                ApiResponse.success(itemCoordinatesService.coordinates(workspaceId, userId)));
    }
}
