package com.ssafy.woojuin.domain.auth.controller;

import com.ssafy.woojuin.domain.auth.dto.EmailAvailabilityResponse;
import com.ssafy.woojuin.domain.auth.dto.LoginRequest;
import com.ssafy.woojuin.domain.auth.dto.SignupRequest;
import com.ssafy.woojuin.domain.auth.dto.TokenRefreshRequest;
import com.ssafy.woojuin.domain.auth.dto.TokenResponse;
import com.ssafy.woojuin.domain.auth.dto.UserProfileResponse;
import com.ssafy.woojuin.domain.auth.service.LoginService;
import com.ssafy.woojuin.domain.auth.service.LogoutService;
import com.ssafy.woojuin.domain.auth.service.SignupService;
import com.ssafy.woojuin.domain.auth.service.TokenRefreshService;
import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignupService signupService;
    private final LoginService loginService;
    private final TokenRefreshService tokenRefreshService;
    private final LogoutService logoutService;
    private final CurrentUserResolver currentUserResolver;

    public AuthController(SignupService signupService, LoginService loginService,
                           TokenRefreshService tokenRefreshService, LogoutService logoutService,
                           CurrentUserResolver currentUserResolver) {
        this.signupService = signupService;
        this.loginService = loginService;
        this.tokenRefreshService = tokenRefreshService;
        this.logoutService = logoutService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/signup")
    public ApiResponse<UserProfileResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(UserProfileResponse.from(signupService.signup(request)));
    }

    /** 회원가입 폼에서 제출 전 실시간으로 이메일 사용 가능 여부를 확인할 때 쓴다. */
    @GetMapping("/check-email")
    public ApiResponse<EmailAvailabilityResponse> checkEmail(@RequestParam String email) {
        return ApiResponse.success(new EmailAvailabilityResponse(signupService.isEmailAvailable(email)));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request,
            // 기기 목록(세션)에 보여줄 이름의 재료 — 없어도 로그인은 된다(이름만 "알 수 없는 기기")
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ApiResponse.success(loginService.login(request, userAgent));
    }

    @PostMapping("/token/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return ApiResponse.success(tokenRefreshService.refresh(request.refreshToken()));
    }

    @AuthenticatedUser
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // sid 는 필터가 access token 에서 꺼내 실어 둔 것 — 그 기기(세션)만 끊는다
        logoutService.logout(currentUserResolver.resolveUserId(), currentUserResolver.resolveSessionId());
        return ApiResponse.success(null);
    }
}
