package com.ssafy.woojuin.domain.item.controller;

import com.ssafy.woojuin.domain.item.dto.ItemGeoResponse;
import com.ssafy.woojuin.domain.item.service.ItemGeoService;
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
 * 지도 뷰 조회 API (FR-032). 읽기 관심사별로 컨트롤러를 쪼개는 기존 방식
 * (ItemSearchController, ItemAiSearchController)과 같은 위치다.
 *
 * <p>{@code data}는 페이지네이션 없는 평면 배열이다 — 지도는 화면의 모든 핀을 한 번에 받아야
 * 하고, 워크스페이스당 좌표 있는 아이템은 많아야 수백 건 수준이라 페이징이 오히려 방해다.
 * 쿼리 파라미터도 없다(카테고리 필터는 응답의 categoryIds로 클라이언트가 처리).
 *
 * <p>나중에 페이지네이션을 넣으면 응답 형태가 바뀌는 breaking change라는 점만 유의.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class ItemGeoController {

    private final ItemGeoService itemGeoService;
    private final CurrentUserResolver currentUserResolver;

    public ItemGeoController(ItemGeoService itemGeoService, CurrentUserResolver currentUserResolver) {
        this.itemGeoService = itemGeoService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping("/items/geo")
    public ResponseEntity<ApiResponse<List<ItemGeoResponse>>> geo(@PathVariable Long workspaceId) {
        Long userId = currentUserResolver.resolveUserId();
        return ResponseEntity.ok(ApiResponse.success(itemGeoService.geoItems(workspaceId, userId)));
    }
}
