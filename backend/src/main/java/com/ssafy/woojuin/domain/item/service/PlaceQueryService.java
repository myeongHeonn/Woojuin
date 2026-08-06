package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.item.dto.NearbyPlacesResponse;
import com.ssafy.woojuin.domain.item.dto.NearbyPlacesResponse.NearbyPlaceResponse;
import com.ssafy.woojuin.domain.location.GeoPoint;
import com.ssafy.woojuin.domain.location.Geocoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * "지금 있는 곳 고르기" 후보 (FR-053, S15P11C105-458).
 *
 * 후보는 카카오맵 장소 페이지가 있는 실제 장소뿐이다 — 저장이 그 링크를 URL 아이템으로
 * 넣는 방식이라, 링크 없는 후보("현재 위치" 같은 것)는 저장할 방법이 없어 내리지 않는다.
 * 주변에 아무것도 없으면 빈 목록이고, 워치는 "주변 장소 없음"을 보여준다.
 * 좌표는 조회에만 쓰고 저장하지 않는다.
 */
@Service
public class PlaceQueryService {

    private final Geocoder geocoder;

    public PlaceQueryService(Geocoder geocoder) {
        this.geocoder = geocoder;
    }

    public NearbyPlacesResponse nearby(double lat, double lng, boolean expand) {
        GeoPoint point = GeoPoint.of(lat, lng)
                .orElseThrow(() -> new IllegalArgumentException("좌표가 올바르지 않습니다"));

        Geocoder.NearbySearch search = geocoder.nearby(point, expand);
        List<NearbyPlaceResponse> candidates = search.places().stream()
                .filter(place -> place.placeUrl() != null)
                .map(NearbyPlaceResponse::from)
                .toList();
        return new NearbyPlacesResponse(candidates, search.expanded());
    }
}
