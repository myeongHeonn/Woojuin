package com.ssafy.woojuin.domain.item.controller;

import com.ssafy.woojuin.domain.item.dto.ItemAiSearchResponse;
import com.ssafy.woojuin.domain.item.service.ItemAiSearchService;
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
 * AI 모드 검색 (프론트 검색창의 AI 토글 ON).
 *
 * <p>키워드 검색(/search)과 경로를 나눈 이유: AI 서포터가 나중에 수정·삭제 같은 액션까지
 * 맡으면 응답에 실릴 것이 검색 결과만이 아니게 된다. 그때 /search의 스키마를 흔들지 않도록
 * 처음부터 분리해 둔다.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/ai")
public class ItemAiSearchController {

    private final ItemAiSearchService itemAiSearchService;
    private final CurrentUserResolver currentUserResolver;

    public ItemAiSearchController(ItemAiSearchService itemAiSearchService,
            CurrentUserResolver currentUserResolver) {
        this.itemAiSearchService = itemAiSearchService;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * {@code q}는 키워드가 아니라 <b>자연어 문장</b>이다 ("그 파스타집 어디였지?").
     * 파라미터 이름을 /search와 맞춰 프론트가 토글만으로 경로를 바꿔 부를 수 있게 했다.
     */
    @AuthenticatedUser
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ItemAiSearchResponse>> search(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "28") int size) {
        Long userId = currentUserResolver.resolveUserId();
        return ResponseEntity.ok(
                ApiResponse.success(itemAiSearchService.search(workspaceId, userId, q, page, size)));
    }
}
