package com.ssafy.woojuin.domain.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 위치 확보 전략 단위 테스트.
 *
 * <p>핵심은 <b>우선순위와 외부 호출 예산</b>이다 — 지도 링크로 좌표를 얻으면 지오코더를
 * 아예 부르지 않아야 하고(쿼터·지연), 정규식이 잡은 주소를 지오코더가 못 풀면 버려야 한다.
 */
@ExtendWith(MockitoExtension.class)
class LocationResolverTest {

    private static final String KAKAO_LINK = "https://map.kakao.com/link/map/cafe,37.5445,127.0561";

    @Mock
    private Geocoder geocoder;

    private LocationResolver resolver;

    @BeforeEach
    void setUp() {
        // 파서·추출기는 순수 함수라 실제 구현을 쓴다 — 목으로 감싸면 통합 지점을 못 본다.
        resolver = new LocationResolver(
                new MapLinkCoordinateParser(), new KoreanAddressExtractor(), geocoder);
    }

    @Test
    void 지도_링크가_있으면_좌표는_URL에서_얻고_주소만_역지오코딩한다() {
        when(geocoder.reverse(new GeoPoint(37.5445, 127.0561)))
                .thenReturn(Optional.of("서울특별시 성동구 아차산로 100"));

        Optional<ResolvedLocation> result = resolver.resolveForUrlItem(
                List.of(KAKAO_LINK), List.of("서울 성동구 아차산로 49 도 본문에 있다"));

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(37.5445);
        assertThat(result.get().lng()).isEqualTo(127.0561);
        assertThat(result.get().address()).isEqualTo("서울특별시 성동구 아차산로 100");
        // 좌표를 URL에서 얻었으므로 본문 주소를 지오코딩하지는 않는다(정방향 호출 없음).
        verify(geocoder, never()).forwardAddress(any());
        verify(geocoder, never()).forwardKeyword(any());
    }

    @Test
    void 지도_링크의_역지오코딩이_실패해도_좌표는_남는다() {
        when(geocoder.reverse(any())).thenReturn(Optional.empty());

        Optional<ResolvedLocation> result = resolver.resolveForUrlItem(
                List.of(KAKAO_LINK), List.of());

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(37.5445);
        assertThat(result.get().address()).isNull();
    }

    @Test
    void 지도_링크가_없으면_본문_주소를_지오코딩한다() {
        when(geocoder.forwardAddress("서울 성동구 아차산로 49")).thenReturn(
                Optional.of(new ResolvedLocation(new GeoPoint(37.5445, 127.0561),
                        "서울 성동구 아차산로17길 49")));

        Optional<ResolvedLocation> result = resolver.resolveForUrlItem(
                List.of("https://blog.naver.com/someone/123"),
                List.of("성수동 이탈리안, 서울 성동구 아차산로 49"));

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(37.5445);
        assertThat(result.get().address()).isEqualTo("서울 성동구 아차산로17길 49");
    }

    @Test
    void 지오코더가_풀지_못한_주소_후보는_버린다() {
        // 정규식 오탐을 무해하게 만드는 장치 — 지오코더가 검증기 역할을 한다.
        when(geocoder.forwardAddress(any())).thenReturn(Optional.empty());

        assertThat(resolver.resolveForUrlItem(
                List.of("https://blog.naver.com/x/1"),
                List.of("서울 성동구 없는길 9999"))).isEmpty();
    }

    @Test
    void 주소를_못_뽑으면_지오코더를_부르지_않는다() {
        assertThat(resolver.resolveForUrlItem(
                List.of("https://example.com/article"),
                List.of("위치와 무관한 아티클 본문"))).isEmpty();

        verifyNoInteractions(geocoder);
    }

    @Test
    void 후보가_비어도_견딘다() {
        assertThat(resolver.resolveForUrlItem(List.of(), List.of())).isEmpty();
        assertThat(resolver.resolveForUrlItem(null, null)).isEmpty();

        verifyNoInteractions(geocoder);
    }

    @Test
    void 좌표만_있으면_역지오코딩으로_주소를_채운다() {
        GeoPoint point = new GeoPoint(37.5445, 127.0561);
        when(geocoder.reverse(point)).thenReturn(Optional.of("서울 성동구 아차산로17길 49"));

        ResolvedLocation result = resolver.resolveForCoordinates(point);

        assertThat(result.lat()).isEqualTo(37.5445);
        assertThat(result.address()).isEqualTo("서울 성동구 아차산로17길 49");
    }

    @Test
    void 역지오코딩이_실패해도_좌표는_살린다() {
        // 핀이 목적이고 주소는 장식이다.
        GeoPoint point = new GeoPoint(37.5445, 127.0561);
        when(geocoder.reverse(point)).thenReturn(Optional.empty());

        ResolvedLocation result = resolver.resolveForCoordinates(point);

        assertThat(result.lat()).isEqualTo(37.5445);
        assertThat(result.lng()).isEqualTo(127.0561);
        assertThat(result.address()).isNull();
    }
}
