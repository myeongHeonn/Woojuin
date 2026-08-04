package com.ssafy.woojuin.domain.workspace.controller;

import com.ssafy.woojuin.domain.workspace.dto.WorkspaceMemberActivityResponse;
import com.ssafy.woojuin.domain.workspace.service.WorkspaceMemberActivityQueryService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/member-activities")
public class WorkspaceMemberActivityController {

    private final WorkspaceMemberActivityQueryService workspaceMemberActivityQueryService;
    private final CurrentUserResolver currentUserResolver;

    public WorkspaceMemberActivityController(WorkspaceMemberActivityQueryService workspaceMemberActivityQueryService,
                                              CurrentUserResolver currentUserResolver) {
        this.workspaceMemberActivityQueryService = workspaceMemberActivityQueryService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceMemberActivityResponse>>> list(@PathVariable Long workspaceId) {
        Long requesterId = currentUserResolver.resolveUserId();
        return ResponseEntity.ok(ApiResponse.success(
                workspaceMemberActivityQueryService.list(workspaceId, requesterId)));
    }
}
