package com.ssafy.woojuin.domain.location;

/**
 * 좌표 주변의 장소 후보 하나 — 워치 위치 저장(FR-053)의 "지금 있는 곳 고르기" 재료.
 * category 는 카카오의 소분류 문자열 그대로다("음식점 > 한식" 수준) — 화면이 마지막 조각만 쓴다.
 */
public record NearbyPlace(
        String name,
        String category,
        int distanceMeters,
        GeoPoint point,
        String address) {
}
