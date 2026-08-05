package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.location.NearbyPlace;

import java.util.List;

/**
 * 주변 장소 후보 목록 (FR-053). 첫 후보는 항상 "현재 위치" — 주변 검색이 0건이어도
 * (지오코딩 꺼짐·허허벌판) 저장할 것이 하나는 남는다.
 */
public record NearbyPlacesResponse(List<NearbyPlaceResponse> candidates) {

    public record NearbyPlaceResponse(
            String name,
            String category,
            int distanceMeters,
            double lat,
            double lng,
            String address) {

        public static NearbyPlaceResponse from(NearbyPlace place) {
            return new NearbyPlaceResponse(
                    place.name(),
                    place.category(),
                    place.distanceMeters(),
                    place.point().lat(),
                    place.point().lng(),
                    place.address());
        }
    }
}
