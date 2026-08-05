package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.item.dto.NearbyPlacesResponse;
import com.ssafy.woojuin.domain.item.dto.NearbyPlacesResponse.NearbyPlaceResponse;
import com.ssafy.woojuin.domain.location.GeoPoint;
import com.ssafy.woojuin.domain.location.Geocoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * "지금 있는 곳 고르기" 후보 (FR-053, S15P11C105-458).
 *
 * 워치가 좌표를 보내면 고를 만한 장소 목록을 돌려준다. 좌표는 저장하지 않는다 —
 * 사용자가 후보를 골라 저장하기 전까지 위치는 서버에 남지 않는다.
 */
@Service
public class PlaceQueryService {

    private final Geocoder geocoder;

    public PlaceQueryService(Geocoder geocoder) {
        this.geocoder = geocoder;
    }

    public NearbyPlacesResponse nearby(double lat, double lng) {
        GeoPoint point = GeoPoint.of(lat, lng)
                .orElseThrow(() -> new IllegalArgumentException("좌표가 올바르지 않습니다"));

        List<NearbyPlaceResponse> candidates = new ArrayList<>();
        // 첫 후보는 항상 "현재 위치" — 주변 검색이 0건이어도 저장할 것이 하나는 남는다.
        // 주소는 지오코딩이 꺼져 있으면(NoOp) 비고, 그래도 좌표 저장에는 지장이 없다
        candidates.add(new NearbyPlaceResponse(
                "현재 위치", null, 0, point.lat(), point.lng(),
                geocoder.reverse(point).orElse(null), null));
        geocoder.nearby(point).forEach(place -> candidates.add(NearbyPlaceResponse.from(place)));

        return new NearbyPlacesResponse(candidates);
    }
}
