package com.ssafy.woojuin.domain.auth.dto;

public record SignupRequest(String email, String password, String nickname) {
}
