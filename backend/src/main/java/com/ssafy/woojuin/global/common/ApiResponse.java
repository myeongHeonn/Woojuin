package com.ssafy.woojuin.global.common;

/**
 * 공통 API 응답 형식 (API 명세서 기준)
 * { "status": 200, "message": "success", "data": {} }
 */
public record ApiResponse<T>(int status, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> of(int status, String message, T data) {
        return new ApiResponse<>(status, message, data);
    }
}
