package com.ssafy.woojuin.global.security.aop;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    //현재 세션에 저장되어 있는 Authentication의 정보를 가져와서 userId를 반환한다.
    public Long resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new AccessDeniedException("인증이 필요합니다");
        }
        return userId;
    }

    /**
     * 호출자의 세션 id — JwtAuthenticationFilter 가 access token 의 sid 클레임을 details 로
     * 실어 둔 것. 로그아웃이 "그 기기만" 끊고, 기기 목록이 "이 기기"를 표시하는 데 쓴다.
     *
     * null 일 수 있다 — 세션 구조 배포 전에 발급된 access token(남은 수명 최대 1시간)은
     * sid 가 없다. 소비처는 null 을 "세션 없음"으로 다룬다(로그아웃이면 지울 세션이 없는 것).
     */
    public String resolveSessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("인증이 필요합니다");
        }
        return authentication.getDetails() instanceof String sessionId ? sessionId : null;
    }
}
