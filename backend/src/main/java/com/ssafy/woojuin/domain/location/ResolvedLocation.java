package com.ssafy.woojuin.domain.location;

/**
 * 확보한 위치. 아이템에 그대로 반영되는 최종 결과물이다.
 *
 * <p>{@code address}는 null일 수 있다 — 지도 링크에서 좌표만 뽑은 경우나 역지오코딩이
 * 실패한 경우다. 핀을 찍는 게 목적이고 주소는 장식이므로, 주소가 없다고 좌표를 버리지 않는다.
 */
public record ResolvedLocation(GeoPoint point, String address) {

    public double lat() {
        return point.lat();
    }

    public double lng() {
        return point.lng();
    }
}
