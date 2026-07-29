package com.ssafy.woojuin.domain.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
 * <p>핵심 계약 둘을 본다 — <b>좌표는 지도에서만 오고</b>(자유 텍스트 주소 경로는 존재하지
 * 않는다), 좌표를 얻었을 때만 <b>역지오코딩 1회</b>로 주소를 채운다.
 */
@ExtendWith(MockitoExtension.class)
class LocationResolverTest {

    private static final String KAKAO_LINK = "https://map.kakao.com/link/map/cafe,37.5445,127.0561";

    /** 네이버 스마트에디터가 글 본문에 심는 지도. pos는 경도가 먼저다. 실측 표본. */
    private static final String NAVER_EMBEDDED_MAP =
            "https://simg.pstatic.net/static.map/v2/map/staticmap.bin?caller=smarteditor"
            + "&markers=color%3A0x11cc73%7Csize%3Amid%7Cpos%3A126.8234182%2035.1909497"
            + "%7CviewSizeRatio%3A0.7%7Ctype%3Ad&w=700&h=315";

    @Mock
    private Geocoder geocoder;

    private LocationResolver resolver;

    @BeforeEach
    void setUp() {
        // 파서는 순수 함수라 실제 구현을 쓴다 — 목으로 감싸면 통합 지점을 못 본다.
        resolver = new LocationResolver(new MapLinkCoordinateParser(), geocoder);
    }

    @Test
    void 지도_링크에서_좌표를_얻고_주소는_역지오코딩한다() {
        when(geocoder.reverse(new GeoPoint(37.5445, 127.0561)))
                .thenReturn(Optional.of("서울특별시 성동구 아차산로 100"));

        Optional<ResolvedLocation> result = resolver.resolveForUrlItem(List.of(KAKAO_LINK));

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(37.5445);
        assertThat(result.get().lng()).isEqualTo(127.0561);
        assertThat(result.get().address()).isEqualTo("서울특별시 성동구 아차산로 100");
    }

    @Test
    void 역지오코딩이_실패해도_좌표는_남는다() {
        when(geocoder.reverse(any())).thenReturn(Optional.empty());

        Optional<ResolvedLocation> result = resolver.resolveForUrlItem(List.of(KAKAO_LINK));

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(37.5445);
        assertThat(result.get().address()).isNull();
    }

    @Test
    void 본문에_임베드된_지도의_핀_좌표를_쓴다() {
        // 맛집 블로그의 실제 시나리오 — 글 URL엔 좌표가 없고 본문 지도에만 있다.
        when(geocoder.reverse(any())).thenReturn(Optional.of("전남광주통합특별시 광산구 임방울대로 347"));

        Optional<ResolvedLocation> result = resolver.resolveForUrlItem(
                List.of("https://m.blog.naver.com/someone/224131224522", NAVER_EMBEDDED_MAP));

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(35.1909497);
        assertThat(result.get().lng()).isEqualTo(126.8234182);
        assertThat(result.get().address()).isEqualTo("전남광주통합특별시 광산구 임방울대로 347");
    }

    @Test
    void 지도가_없으면_본문에_주소가_적혀_있어도_위치를_만들지_않는다() {
        // 의도된 동작이다. 지식·기술 글에 핀이 뜨는 것을 막기 위해 자유 텍스트 주소 경로를
        // 없앴다 — 이유는 LocationResolver javadoc 참고. 지오코더를 아예 부르지 않는다.
        Optional<ResolvedLocation> result = resolver.resolveForUrlItem(
                List.of("https://blog.example.com/post/1"));

        assertThat(result).isEmpty();
        verifyNoInteractions(geocoder);
    }

    @Test
    void 지도가_아닌_URL만_있으면_지오코더를_부르지_않는다() {
        assertThat(resolver.resolveForUrlItem(
                List.of("https://ko.wikipedia.org/wiki/Java"))).isEmpty();

        verifyNoInteractions(geocoder);
    }

    @Test
    void 후보가_비어도_견딘다() {
        assertThat(resolver.resolveForUrlItem(List.of())).isEmpty();
        assertThat(resolver.resolveForUrlItem(null)).isEmpty();

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
