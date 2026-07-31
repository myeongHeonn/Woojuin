package com.ssafy.woojuin.domain.auth.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long userId) {
        super("AUTH_404: 사용자를 찾을 수 없습니다 (userId=" + userId + ")");
    }
}
