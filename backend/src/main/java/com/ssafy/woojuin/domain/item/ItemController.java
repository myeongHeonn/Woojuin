package com.ssafy.woojuin.domain.item;

import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.global.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 저장 API 뼈대 (묶음 C). workspaceId/userId는 워크스페이스·인증 도메인(묶음 B)이
 * 완성되기 전까지 path/header로 임시 수신한다.
 * TODO(FR-001): JWT 붙으면 X-User-Id 헤더 대신 SecurityContext에서 userId 추출로 교체 (성훈 담당)
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ItemCreateResponse>> createFromRequest(
            @PathVariable Long workspaceId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ItemCreateRequest request) {
        ItemCreateResponse response = itemService.createFromRequest(workspaceId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(201, "success", response));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ItemCreateResponse>> createFromImage(
            @PathVariable Long workspaceId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestPart MultipartFile file) {
        ItemCreateResponse response = itemService.createFromImage(workspaceId, userId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(201, "success", response));
    }
}
