package com.ssafy.woojuin.domain.location;

import java.util.Optional;

/**
 * WGS84 좌표. 지도 SDK가 미정이라 특정 API 형식이 아닌 lat/lng 원시값으로만 다룬다
 * (AGENTS.md).
 *
 * <p>레코드 자체는 검증하지 않고, <b>생성은 항상 팩토리를 통한다</b>. 좌표의 출처가 전부
 * 신뢰할 수 없는 텍스트(URL 정규식 캡처, 카카오 응답의 문자열 x/y, 사진 EXIF)이기 때문에
 * 예외를 던지는 대신 {@code Optional.empty()}로 조용히 걸러내는 쪽이 호출부를 단순하게
 * 만든다 — 좌표를 못 얻는 건 실패가 아니라 정상 결과다.
 */
public record GeoPoint(double lat, double lng) {

    // 대한민국 대략 bbox. 카카오·네이버는 국내 좌표만 돌려주므로, 이 밖으로 나오면
    // 파서가 lat/lng를 뒤바꿔 읽었거나 엉뚱한 숫자를 잡은 것이다.
    static final double KOREA_MIN_LAT = 33.0;
    static final double KOREA_MAX_LAT = 38.7;
    static final double KOREA_MIN_LNG = 124.5;
    static final double KOREA_MAX_LNG = 132.0;

    public static Optional<GeoPoint> of(double lat, double lng) {
        return isPlausible(lat, lng) ? Optional.of(new GeoPoint(lat, lng)) : Optional.empty();
    }

    /**
     * 문자열 좌표에서 만든다. 카카오 로컬 API가 x/y를 <b>문자열</b>로 주고, URL 정규식
     * 캡처도 문자열이라 이 경로가 기본이다.
     */
    public static Optional<GeoPoint> parse(String lat, String lng) {
        if (lat == null || lng == null) {
            return Optional.empty();
        }
        try {
            return of(Double.parseDouble(lat.trim()), Double.parseDouble(lng.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 국내 제공자(카카오·네이버)에서 얻은 좌표인지 상식 검사. */
    public boolean isInKorea() {
        return lat >= KOREA_MIN_LAT && lat <= KOREA_MAX_LAT
                && lng >= KOREA_MIN_LNG && lng <= KOREA_MAX_LNG;
    }

    /**
     * 0,0은 거부한다. 기니만의 실제 좌표이긴 하지만, 실제로 이 값이 나오는 경로는 EXIF가
     * 비었거나 파싱이 실패한 경우가 압도적이다.
     */
    private static boolean isPlausible(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= -90 && lat <= 90
                && lng >= -180 && lng <= 180
                && !(lat == 0 && lng == 0);
    }
}
