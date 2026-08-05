package com.ssafy.woojuin.domain.item.controller;

import com.ssafy.woojuin.domain.item.dto.NearbyPlacesResponse;
import com.ssafy.woojuin.domain.item.service.PlaceQueryService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주변 장소 후보 (FR-053, S15P11C105-458) — 워치 "지금 있는 곳 저장"의 조회 절반.
 * 저장은 별도 API 가 아니다 — 후보의 카카오맵 링크를 기존 URL 아이템으로 저장한다.
 * URL 파이프라인이 크롤로 좌표(스태틱맵)·제목·요약을 만들므로 장소 전용 저장이 필요 없다.
 */
@RestController
public class PlaceController {

    private final PlaceQueryService placeQueryService;
    private final CurrentUserResolver currentUserResolver;

    public PlaceController(PlaceQueryService placeQueryService,
                           CurrentUserResolver currentUserResolver) {
        this.placeQueryService = placeQueryService;
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
}
