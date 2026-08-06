package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.location.NearbyPlace;

import java.util.List;

/**
 * 주변 장소 후보 목록 (FR-053) — 카카오맵 링크가 있는 실제 장소만, 거리순.
 * {@code expanded}가 참이면 전 카테고리를 이미 뒤진 결과라는 뜻이다 — 워치는 이때
 * "주변 더 찾기"를 숨긴다(다시 물어도 같은 답이므로).
 */
public record NearbyPlacesResponse(List<NearbyPlaceResponse> candidates, boolean expanded) {

    public record NearbyPlaceResponse(
            String name,
            String category,
            int distanceMeters,
            double lat,
            double lng,
            String address,
            String placeUrl) {

        public static NearbyPlaceResponse from(NearbyPlace place) {
            return new NearbyPlaceResponse(
                    place.name(),
                    place.category(),
                    place.distanceMeters(),
                    place.point().lat(),
                    place.point().lng(),
                    place.address(),
                    place.placeUrl());
        }
    }
}
