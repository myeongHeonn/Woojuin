package com.ssafy.woojuin.domain.workspace.controller;

import com.ssafy.woojuin.domain.workspace.dto.ItemActivityResponse;
import com.ssafy.woojuin.domain.workspace.service.WorkspaceItemActivityService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/item-activity")
public class WorkspaceItemActivityController {

    private final WorkspaceItemActivityService workspaceItemActivityService;
    private final CurrentUserResolver currentUserResolver;

    public WorkspaceItemActivityController(WorkspaceItemActivityService workspaceItemActivityService,
                                            CurrentUserResolver currentUserResolver) {
        this.workspaceItemActivityService = workspaceItemActivityService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping
    public ResponseEntity<ApiResponse<ItemActivityResponse>> hasNewActivity(@PathVariable Long workspaceId) {
        Long userId = currentUserResolver.resolveUserId();
        boolean hasNewActivity = workspaceItemActivityService.hasNewActivity(workspaceId, userId);
        return ResponseEntity.ok(ApiResponse.success(new ItemActivityResponse(hasNewActivity)));
    }

    @AuthenticatedUser
    @PutMapping("/last-seen")
    public ResponseEntity<ApiResponse<Void>> updateLastSeen(@PathVariable Long workspaceId) {
        Long userId = currentUserResolver.resolveUserId();
        workspaceItemActivityService.updateLastSeen(workspaceId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
