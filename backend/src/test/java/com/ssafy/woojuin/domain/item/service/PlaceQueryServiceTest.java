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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceQueryServiceTest {

    @Mock
    private Geocoder geocoder;

    @InjectMocks
    private PlaceQueryService placeQueryService;

    @Test
    @DisplayName("첫 후보는 항상 '현재 위치' — 그 뒤로 주변 장소가 이어진다")
    void nearby_currentLocationFirst() {
        when(geocoder.reverse(any())).thenReturn(Optional.of("서울 성동구 성수동2가"));
        when(geocoder.nearby(any())).thenReturn(List.of(
                new NearbyPlace("온화정", "음식점 > 한식", 120,
                        new GeoPoint(37.5446, 127.0562), "서울 성동구 성수이로 100")));

        NearbyPlacesResponse response = placeQueryService.nearby(37.5445, 127.0561);

        assertThat(response.candidates()).hasSize(2);
        assertThat(response.candidates().get(0).name()).isEqualTo("현재 위치");
        assertThat(response.candidates().get(0).address()).isEqualTo("서울 성동구 성수동2가");
        assertThat(response.candidates().get(0).lat()).isEqualTo(37.5445);
        assertThat(response.candidates().get(1).name()).isEqualTo("온화정");
        assertThat(response.candidates().get(1).distanceMeters()).isEqualTo(120);
    }

    @Test
    @DisplayName("지오코딩이 꺼져 있어도(주변 0건·주소 없음) '현재 위치' 하나는 남는다")
    void nearby_geocodingOff_currentLocationOnly() {
        when(geocoder.reverse(any())).thenReturn(Optional.empty());
        when(geocoder.nearby(any())).thenReturn(List.of());

        NearbyPlacesResponse response = placeQueryService.nearby(37.5445, 127.0561);

        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).name()).isEqualTo("현재 위치");
        assertThat(response.candidates().get(0).address()).isNull();
    }

    @Test
    @DisplayName("범위 밖 좌표는 400")
    void nearby_invalidCoordinates_throws() {
        assertThatThrownBy(() -> placeQueryService.nearby(123.0, 999.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
