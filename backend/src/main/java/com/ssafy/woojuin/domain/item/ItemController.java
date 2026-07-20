package com.ssafy.woojuin.domain.item;

import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.common.ItemStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 워크스페이스 단위 아이템 API (저장·목록·휴지통 목록).
 * 개별 아이템 단위 작업은 ItemDetailController 참고.
 *
 * workspaceId/userId는 워크스페이스·인증 도메인(묶음 B)이 완성되기 전까지
 * path/header로 임시 수신한다.
 * TODO(FR-001): JWT 붙으면 X-User-Id 헤더 대신 SecurityContext에서 userId 추출로 교체 (성훈 담당)
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ItemCreateResponse>> createFromRequest(
            @PathVariable Long workspaceId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ItemCreateRequest request) {
        ItemCreateResponse response = itemService.createFromRequest(workspaceId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(201, "success", response));
    }

    @PostMapping(path = "/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ItemCreateResponse>> createFromImage(
            @PathVariable Long workspaceId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestPart MultipartFile file) {
        ItemCreateResponse response = itemService.createFromImage(workspaceId, userId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(201, "success", response));
    }

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<ItemListResponse>> list(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) ItemType type,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ItemListResponse response = itemService.list(workspaceId, type, status, favorite, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 휴지통 목록 (FR-036). 삭제된 지 오래된 순이 아니라 최근 삭제 순으로 보여준다. */
    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<ItemListResponse>> listTrash(
            @PathVariable Long workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(itemService.listTrash(workspaceId, page, size)));
    }
}
