package com.ssafy.woojuin.domain.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 장소 저장 요청 (FR-053) — 워치가 주변 후보에서 고른 장소를 그대로 실어 보낸다.
 * memo 는 선택 — 워치엔 키보드가 없어 보통 비지만, 다른 클라이언트를 막을 이유가 없다.
 * placeUrl 은 후보에 실려 온 카카오맵 장소 페이지 — 아이템의 url 로 보존된다.
 */
public record PlaceSaveRequest(
        @NotBlank(message = "장소 이름은 필수입니다") String name,
        @NotNull(message = "위도는 필수입니다") Double lat,
        @NotNull(message = "경도는 필수입니다") Double lng,
        String address,
        String memo,
        String placeUrl) {
}
