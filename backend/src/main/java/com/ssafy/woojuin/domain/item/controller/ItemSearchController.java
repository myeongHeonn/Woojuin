package com.ssafy.woojuin.domain.item.controller;

import com.ssafy.woojuin.domain.item.dto.ItemSearchResponse;
import com.ssafy.woojuin.domain.item.service.ItemSearchService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 검색 API (FR-030). 응답 {@link ItemSearchResponse}는 목록 조회와 같은 필드에
 * partialMatch 하나만 더 붙는다 — content 배열이 목록과 동일해서 클라이언트가 아이템 카드
 * 컴포넌트를 그대로 재사용할 수 있다.
 * (API 명세서 예시는 preview·categories·imageUrl이 없는 옛 형태라 실제 응답과 다르다.)
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class ItemSearchController {

    private final ItemSearchService itemSearchService;
    private final CurrentUserResolver currentUserResolver;

    public ItemSearchController(ItemSearchService itemSearchService, CurrentUserResolver currentUserResolver) {
        this.itemSearchService = itemSearchService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ItemSearchResponse>> search(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = currentUserResolver.resolveUserId();
        return ResponseEntity.ok(
                ApiResponse.success(itemSearchService.search(workspaceId, userId, q, page, size)));
    }
}
