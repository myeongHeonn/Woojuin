package com.ssafy.woojuin.domain.location;

import java.util.Optional;

/**
 * {@link Geocoder} 스텁 — {@code KAKAO_REST_API_KEY}가 없을 때 올라간다.
 *
 * <p>지오코딩만 꺼지고 위치 기능이 전부 죽는 게 아니다. 지도 공유 링크 좌표 파싱과 사진 EXIF
 * 읽기는 순수 로컬 연산이라 그대로 동작하므로, <b>API 키 0개로도 지도에 핀이 뜨는 상태를
 * 시연할 수 있다.</b> 잃는 건 "주소 문자열 → 좌표" 변환과 EXIF 좌표의 주소 표기뿐이다.
 *
 * <p>{@link GeocoderConfig}가 키 유무를 보고 등록한다.
 */
public class NoOpGeocoder implements Geocoder {

    @Override
    public Optional<ResolvedLocation> forwardAddress(String address) {
        return Optional.empty();
    }

    @Override
    public Optional<ResolvedLocation> forwardKeyword(String keyword) {
        return Optional.empty();
    }

    @Override
    public Optional<String> reverse(GeoPoint point) {
        return Optional.empty();
    }

    @Override
    public java.util.List<NearbyPlace> nearby(GeoPoint point) {
        return java.util.List.of();
    }
}
