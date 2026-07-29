package com.ssafy.woojuin.domain.location;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 키가 있으면 카카오, 없으면 NoOp.
 *
 * <p>{@code KAKAO_REST_API_KEY=} 처럼 빈 줄만 남은 경우가 "키 있음"으로 잡히면 아이템을
 * 저장할 때마다 401을 맞고 조용히 실패하는 상태가 된다 — AiQueryConfig가 기록한 것과 같은
 * 함정이라 blank까지 본다.
 */
class GeocoderConfigTest {

    private final GeocoderConfig config = new GeocoderConfig();

    private Geocoder geocoderFor(String restApiKey) {
        return config.geocoder(new ObjectMapper(), restApiKey, "https://example.com", 1000L);
    }

    @Test
    void 키가_있으면_카카오_지오코더() {
        assertThat(geocoderFor("rest-key")).isInstanceOf(KakaoLocalGeocoder.class);
    }

    @Test
    void 키가_없으면_NoOp() {
        assertThat(geocoderFor("")).isInstanceOf(NoOpGeocoder.class);
        assertThat(geocoderFor(null)).isInstanceOf(NoOpGeocoder.class);
    }

    @Test
    void 공백뿐인_키는_없는_것으로_본다() {
        assertThat(geocoderFor("   ")).isInstanceOf(NoOpGeocoder.class);
    }

    @Test
    void NoOp은_모든_변환에서_빈_결과를_준다() {
        Geocoder geocoder = geocoderFor("");

        assertThat(geocoder.forwardAddress("서울 성동구 아차산로 49")).isEmpty();
        assertThat(geocoder.forwardKeyword("성수동 카페")).isEmpty();
        assertThat(geocoder.reverse(new GeoPoint(37.5445, 127.0561))).isEmpty();
    }
}
