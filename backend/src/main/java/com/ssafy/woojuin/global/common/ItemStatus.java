package com.ssafy.woojuin.global.common;

/**
 * 저장 항목 비동기 처리 상태 (FR-025)
 * 저장 즉시 PROCESSING으로 응답 → 워커 처리 후 DONE/PARTIAL/FAILED
 */
public enum ItemStatus {
    PROCESSING,
    DONE,
    PARTIAL,   // 트랙 A(미리보기)만 성공, 트랙 B(본문 확보) 실패
    FAILED
}
