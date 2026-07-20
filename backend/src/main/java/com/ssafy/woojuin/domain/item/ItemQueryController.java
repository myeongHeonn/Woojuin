package com.ssafy.woojuin.domain.item;

import com.ssafy.woojuin.domain.item.dto.ItemResponse;
import com.ssafy.woojuin.domain.item.dto.ItemStatusResponse;
import com.ssafy.woojuin.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세서 기준 아이템 상세/상태는 workspaceId 없이 /items/{itemId}로 조회한다
 * (아이템 자체가 workspaceId를 갖고 있어 경로에 중복될 필요가 없음).
 */
@RestController
@RequestMapping("/api/items/{itemId}")
public class ItemQueryController {

    private final ItemService itemService;

    public ItemQueryController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ItemResponse>> detail(@PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success(itemService.getDetail(itemId)));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ItemStatusResponse>> status(@PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success(itemService.getStatus(itemId)));
    }
}
