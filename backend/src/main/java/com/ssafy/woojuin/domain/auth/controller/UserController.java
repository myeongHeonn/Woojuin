package com.ssafy.woojuin.domain.auth.controller;

import com.ssafy.woojuin.domain.ai.usage.AiUsageResponse;
import com.ssafy.woojuin.domain.ai.usage.AiUsageService;
import com.ssafy.woojuin.domain.auth.dto.UpdateProfileRequest;
import com.ssafy.woojuin.domain.auth.dto.UserProfileResponse;
import com.ssafy.woojuin.domain.auth.service.UserProfileService;
import com.ssafy.woojuin.domain.auth.service.UserWithdrawalService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserProfileService userProfileService;
    private final UserWithdrawalService userWithdrawalService;
    private final AiUsageService aiUsageService;
    private final CurrentUserResolver currentUserResolver;

    public UserController(UserProfileService userProfileService, UserWithdrawalService userWithdrawalService,
                          AiUsageService aiUsageService, CurrentUserResolver currentUserResolver) {
        this.userProfileService = userProfileService;
        this.userWithdrawalService = userWithdrawalService;
        this.aiUsageService = aiUsageService;
        this.currentUserResolver = currentUserResolver;
    }

    @AuthenticatedUser
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile() {
        Long userId = currentUserResolver.resolveUserId();
        return ApiResponse.success(userProfileService.getProfile(userId));
    }

    @AuthenticatedUser
    @GetMapping("/me/ai-usage")
    public ApiResponse<AiUsageResponse> getMyAiUsage() {
        Long userId = currentUserResolver.resolveUserId();
        return ApiResponse.success(aiUsageService.getUsage(userId));
    }

    @AuthenticatedUser
    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = currentUserResolver.resolveUserId();
        return ApiResponse.success(userProfileService.updateProfile(userId, request));
    }

    @AuthenticatedUser
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw() {
        Long userId = currentUserResolver.resolveUserId();
        userWithdrawalService.withdraw(userId);
        return ApiResponse.success(null);
    }
}
