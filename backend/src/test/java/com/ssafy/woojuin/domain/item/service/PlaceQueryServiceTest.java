package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.item.dto.NearbyPlacesResponse;
import com.ssafy.woojuin.domain.location.GeoPoint;
import com.ssafy.woojuin.domain.location.Geocoder;
import com.ssafy.woojuin.domain.location.NearbyPlace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceQueryServiceTest {

    @Mock
    private Geocoder geocoder;

    @InjectMocks
    private PlaceQueryService placeQueryService;

    @Test
    @DisplayName("후보는 카카오맵 링크가 있는 실제 장소뿐이다 — 저장이 그 링크로 이루어진다")
    void nearby_returnsOnlyLinkedPlaces() {
        when(geocoder.nearby(any(), anyBoolean())).thenReturn(new Geocoder.NearbySearch(List.of(
                new NearbyPlace("온화정", "음식점 > 한식", 120,
                        new GeoPoint(37.5446, 127.0562), "서울 성동구 성수이로 100",
                        "http://place.map.kakao.com/12345"),
                new NearbyPlace("링크 없는 곳", "음식점", 50,
                        new GeoPoint(37.5447, 127.0563), null, null)), false));

        NearbyPlacesResponse response = placeQueryService.nearby(37.5445, 127.0561, false);

        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).name()).isEqualTo("온화정");
        assertThat(response.candidates().get(0).placeUrl()).isEqualTo("http://place.map.kakao.com/12345");
        // 지오코더의 expanded가 그대로 실려 나간다 — 워치의 "주변 더 찾기" 노출 기준
        assertThat(response.expanded()).isFalse();
    }

    @Test
    @DisplayName("주변에 장소가 없으면 빈 목록 — 워치가 '주변 장소 없음'을 보여준다")
    void nearby_empty() {
        when(geocoder.nearby(any(), anyBoolean()))
                .thenReturn(new Geocoder.NearbySearch(List.of(), true));

        NearbyPlacesResponse response = placeQueryService.nearby(37.5445, 127.0561, false);

        assertThat(response.candidates()).isEmpty();
        assertThat(response.expanded()).isTrue();
    }

    @Test
    @DisplayName("범위 밖 좌표는 400")
    void nearby_invalidCoordinates_throws() {
        assertThatThrownBy(() -> placeQueryService.nearby(123.0, 999.0, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
