package com.ssafy.woojuin.domain.item.controller;

import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.dto.NearbyPlacesResponse;
import com.ssafy.woojuin.domain.item.dto.PlaceSaveRequest;
import com.ssafy.woojuin.domain.item.service.ItemService;
import com.ssafy.woojuin.domain.item.service.PlaceQueryService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 위치 저장 (FR-053, S15P11C105-458) — 워치 "지금 있는 곳 저장"의 서버 절반.
 * 후보 조회 → 사용자가 고른 장소를 아이템으로 저장, 두 걸음이 전부다.
 */
@RestController
public class PlaceController {

    private final PlaceQueryService placeQueryService;
    private final ItemService itemService;
    private final CurrentUserResolver currentUserResolver;

    public PlaceController(PlaceQueryService placeQueryService, ItemService itemService,
                           CurrentUserResolver currentUserResolver) {
        this.placeQueryService = placeQueryService;
        this.itemService = itemService;
        this.currentUserResolver = currentUserResolver;
    }

    /** 좌표 주변의 저장 후보. 좌표는 조회에만 쓰고 남기지 않는다 */
    @AuthenticatedUser
    @GetMapping("/api/places/nearby")
    public ApiResponse<NearbyPlacesResponse> nearby(
            @RequestParam double lat, @RequestParam double lng) {
        currentUserResolver.resolveUserId();
        return ApiResponse.success(placeQueryService.nearby(lat, lng));
    }

    /** 고른 장소를 아이템으로 저장 — AI 처리 없이 저장 즉시 DONE (ItemService.createPlace 참고) */
    @AuthenticatedUser
    @PostMapping("/api/workspaces/{workspaceId}/places")
    public ResponseEntity<ApiResponse<ItemCreateResponse>> save(
            @PathVariable Long workspaceId,
            @Valid @RequestBody PlaceSaveRequest request) {
        Long userId = currentUserResolver.resolveUserId();
        ItemCreateResponse response = itemService.createPlace(workspaceId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(201, "success", response));
    }
}
