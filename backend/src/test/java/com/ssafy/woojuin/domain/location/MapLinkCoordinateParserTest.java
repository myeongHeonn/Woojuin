package com.ssafy.woojuin.domain.location;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 지도 링크 좌표 파싱 단위 테스트.
 *
 * <p>이 기능에서 버그가 실제로 사는 곳이라 가장 촘촘하게 본다 — 제공자마다 파라미터 이름과
 * 위도·경도 순서가 다르고, 좌표처럼 보이지만 좌표가 아닌 값(투영 좌표, 장소 id, 뷰포트 중심)이
 * 섞여 있다.
 */
class MapLinkCoordinateParserTest {

    private final MapLinkCoordinateParser parser = new MapLinkCoordinateParser();

    private void assertCoord(Optional<GeoPoint> actual, double lat, double lng) {
        assertThat(actual).isPresent();
        assertThat(actual.get().lat()).isEqualTo(lat);
        assertThat(actual.get().lng()).isEqualTo(lng);
    }

    // ---------- 구글 ----------

    @Test
    void 구글_장소_링크의_3d4d_좌표를_읽는다() {
        String url = "https://www.google.com/maps/place/%EC%84%9C%EC%9A%B8%EC%88%B2/"
                + "@37.5444,127.0374,17z/data=!3m1!4b1!4m6!3m5!1s0x1234!8m2!3d37.5445!4d127.0561";

        assertCoord(parser.parse(url), 37.5445, 127.0561);
    }

    @Test
    void 구글_3d4d가_뷰포트_중심보다_우선한다() {
        // @ 뒤는 지도 화면 중심이고 !3d!4d 가 핀의 실제 좌표다. 둘이 다를 때 후자를 써야 한다.
        String url = "https://www.google.com/maps/place/X/@37.1111,127.1111,17z/data=!8m2!3d37.5445!4d127.0561";

        assertCoord(parser.parse(url), 37.5445, 127.0561);
    }

    @Test
    void 구글_3d4d가_없으면_뷰포트_중심을_쓴다() {
        String url = "https://www.google.com/maps/@37.5445,127.0561,15z";

        assertCoord(parser.parse(url), 37.5445, 127.0561);
    }

    @Test
    void 구글_Maps_URLs_API_query_파라미터를_읽는다() {
        String url = "https://www.google.com/maps/search/?api=1&query=37.5445,127.0561";

        assertCoord(parser.parse(url), 37.5445, 127.0561);
    }

    @Test
    void 구글_query가_퍼센트_인코딩된_쉼표여도_읽는다() {
        String url = "https://www.google.com/maps/search/?api=1&query=37.5445%2C127.0561";

        assertCoord(parser.parse(url), 37.5445, 127.0561);
    }

    @Test
    void 구글은_국외_좌표도_허용한다() {
        // 국내 bbox 검사는 카카오·네이버 전용이다. 파리 좌표를 버리면 안 된다.
        String url = "https://www.google.com/maps/@48.8584,2.2945,17z";

        assertCoord(parser.parse(url), 48.8584, 2.2945);
    }

    @Test
    void maps_google_com_호스트도_인식한다() {
        assertCoord(parser.parse("https://maps.google.com/maps?ll=37.5445,127.0561"), 37.5445, 127.0561);
    }

    @Test
    void 구글_길찾기_도착지_좌표를_읽는다() {
        assertCoord(parser.parse("https://www.google.com/maps/dir/?api=1&destination=37.5445,127.0561"),
                37.5445, 127.0561);
        assertCoord(parser.parse("https://maps.google.com/maps?daddr=37.5445%2C127.0561"),
                37.5445, 127.0561);
    }

    @Test
    void 구글_스태틱맵_center는_요청자_IP_위치라_거부한다() {
        // 실측: 서울시청·부산역·에펠탑 지도 페이지를 각각 요청했는데 og:image의 center 값이
        // 셋 다 동일했다(요청한 사무실 위치). 이걸 믿으면 모든 구글 지도 링크가 서버
        // 데이터센터에 핀을 꽂으면서 그럴듯해 보인다. 이 테스트가 그 회귀를 막는다.
        String ogImage = "https://maps.google.com/maps/api/staticmap?center=35.2062608%2C126.81437965"
                + "&zoom=15&size=900x900&language=en&sensor=false&key=AIza";

        assertThat(parser.parse(ogImage)).isEmpty();
        assertThat(parser.parse("https://maps.googleapis.com/maps/api/staticmap?center=35.2062,126.8143"))
                .isEmpty();
    }

    // ---------- 카카오 ----------

    @Test
    void 카카오_공유링크의_이름_위도_경도를_읽는다() {
        String url = "https://map.kakao.com/link/map/%EC%84%B1%EC%88%98%EB%8F%99%EC%B9%B4%ED%8E%98,37.5445,127.0561";

        assertCoord(parser.parse(url), 37.5445, 127.0561);
    }

    @Test
    void 카카오_link_to_형태도_읽는다() {
        assertCoord(parser.parse("https://map.kakao.com/link/to/cafe,37.5445,127.0561"), 37.5445, 127.0561);
    }

    @Test
    void 카카오_이름에_쉼표가_있어도_마지막_두_숫자를_잡는다() {
        assertCoord(parser.parse("https://map.kakao.com/link/map/A,B,37.5445,127.0561"), 37.5445, 127.0561);
    }

    @Test
    void 카카오_장소id만_있는_링크는_좌표가_없다() {
        // /link/map/{placeId} 는 쉼표가 없어 매칭되지 않아야 한다. 매칭되면 id를 좌표로 쓴다.
        assertThat(parser.parse("https://map.kakao.com/link/map/26338954")).isEmpty();
        assertThat(parser.parse("https://place.map.kakao.com/26338954")).isEmpty();
    }

    @Test
    void 카카오_WCONGNAMUL_투영좌표는_거부한다() {
        // urlX/urlY 는 WGS84가 아니다. 그대로 저장하면 핀이 바다에 꽂힌다.
        assertThat(parser.parse("https://map.kakao.com/?urlX=507000&urlY=1120000")).isEmpty();
    }

    // ---------- 카카오 장소 페이지의 스태틱맵 (경도가 먼저!) ----------

    /**
     * 카카오맵 앱의 '공유'가 주는 {@code place.map.kakao.com/{id}}는 URL에 좌표가 없지만,
     * 페이지가 미리보기로 싣는 스태틱맵 이미지 URL에 정확한 좌표가 들어있다. 실측 표본이다
     * (수완초밥, 장소 id 15586602).
     */
    private static final String KAKAO_STATICMAP =
            "http://staticmap.kakao.com/staticmap/og?type=place&srs=wgs84&size=400x200"
            + "&service=placeweb&m=126.81519384985194%2C35.18968663709063";

    @Test
    void 카카오_스태틱맵의_m_파라미터는_경도가_먼저다() {
        // 이 순서를 뒤집으면 위도 126은 범위를 벗어나 조용히 버려지거나(운이 좋을 때),
        // 값에 따라 중국 어딘가에 핀이 꽂힌다. 스태틱맵 지원의 핵심 계약이다.
        assertCoord(parser.parse(KAKAO_STATICMAP), 35.18968663709063, 126.81519384985194);
    }

    @Test
    void 스태틱맵에_srs_wgs84_표기가_없으면_쓰지_않는다() {
        // 카카오가 이 파라미터를 투영 좌표계로 바꾸는 날, 바다에 핀을 꽂는 대신 포기해야 한다.
        String noSrs = "http://staticmap.kakao.com/staticmap/og?type=place&size=400x200"
                + "&m=126.81519384985194%2C35.18968663709063";

        assertThat(parser.parse(noSrs)).isEmpty();
    }

    @Test
    void 스태틱맵은_링크_좌표보다_뒤_순위다() {
        // 후보 순서(링크 먼저, 페이지 에셋 나중)가 지켜지는지. 링크에 박힌 핀 좌표가 더 정확하다.
        Optional<GeoPoint> result = parser.parse(
                "https://map.kakao.com/link/map/cafe,37.5445,127.0561", KAKAO_STATICMAP);

        assertCoord(result, 37.5445, 127.0561);
    }

    @Test
    void 장소id_링크는_스태틱맵이_함께_오면_좌표를_얻는다() {
        // 실제 시나리오 — place URL 자체는 좌표가 없고, 같은 페이지의 twitter:image가 채워준다.
        Optional<GeoPoint> result = parser.parse(
                "https://place.map.kakao.com/15586602", KAKAO_STATICMAP);

        assertCoord(result, 35.18968663709063, 126.81519384985194);
    }

    @Test
    void 스태틱맵_호스트는_카카오_링크_패턴으로_오독되지_않는다() {
        // staticmap.kakao.com 은 map.kakao.com 으로 끝나서 호스트 판별 순서가 틀리면
        // /link/map/ 패턴 쪽으로 흘러가 좌표를 못 찾는다.
        assertCoord(parser.parse(KAKAO_STATICMAP), 35.18968663709063, 126.81519384985194);
        assertThat(parser.parse("http://staticmap.kakao.com/staticmap/og?srs=wgs84&size=400x200"))
                .isEmpty();
    }

    // ---------- 네이버 ----------

    @Test
    void 네이버_lat_lng_파라미터를_읽는다() {
        assertCoord(parser.parse("https://map.naver.com/v5/search/cafe?lng=127.0561&lat=37.5445"),
                37.5445, 127.0561);
    }

    @Test
    void 네이버_x_y_파라미터는_x가_경도다() {
        assertCoord(parser.parse("https://map.naver.com/v5/entry/place/123?x=127.0561&y=37.5445"),
                37.5445, 127.0561);
    }

    // ---------- 네이버 본문 임베드 지도 (경도가 먼저!) ----------

    /**
     * 네이버 스마트에디터가 글 본문에 심는 정적 지도. 실측 표본이고, 값이 퍼센트 인코딩돼 있다.
     * 글쓴이가 직접 찍은 핀이라 본문 주소를 지오코딩하는 것보다 정확하다.
     */
    private static final String NAVER_STATICMAP =
            "https://simg.pstatic.net/static.map/v2/map/staticmap.bin?caller=smarteditor"
            + "&markers=color%3A0x11cc73%7Csize%3Amid%7Cpos%3A126.8234182%2035.1909497"
            + "%7CviewSizeRatio%3A0.7%7Ctype%3Ad&w=700&h=315&scale=2&dataversion=176.29";

    @Test
    void 네이버_임베드_지도의_pos는_경도가_먼저다() {
        assertCoord(parser.parse(NAVER_STATICMAP), 35.1909497, 126.8234182);
    }

    @Test
    void 네이버_임베드_지도는_구분자가_플러스여도_읽는다() {
        // 리터럴 공백은 URI.create가 거부하므로 애초에 여기까지 오지 않는다.
        String plusSeparated = "https://simg.pstatic.net/static.map/v2/map/staticmap.bin"
                + "?markers=pos:126.8234182+35.1909497&w=700";

        assertCoord(parser.parse(plusSeparated), 35.1909497, 126.8234182);
    }

    @Test
    void 네이버_임베드_지도의_좌표계가_WGS84가_아니면_거부한다() {
        // crs가 생략되면 기본값이 WGS84라서 쓰고, 다른 좌표계가 명시되면 포기한다.
        String tm = "https://simg.pstatic.net/static.map/v2/map/staticmap.bin"
                + "?crs=EPSG%3A3857&markers=pos%3A126.8234182%2035.1909497&w=700";

        assertThat(parser.parse(tm)).isEmpty();
    }

    // ---------- 네이버 장소 페이지의 '길찾기' 링크 (경도가 먼저!) ----------

    /**
     * 네이버 지도 링크는 URL에 좌표가 없고 페이지도 SPA 껍데기다. 모바일 장소 페이지의 '길찾기'
     * 링크가 장소 id와 좌표를 함께 담는다. 실측 표본(푸드박스 광주점, id 1301934134).
     */
    private static final String NAVER_DIRECTIONS =
            "https://m.search.naver.com/search.naver?where=m&query=%EB%B9%A0%EB%A5%B8%EA%B8%B8"
            + "&nso_path=placeType%5Eplace%3Bname%5E%3Baddress%5E%3Bcode%5E1301934134"
            + "%3Blongitude%5E126.7989859%3Blatitude%5E35.1820806%7Cobjtype%5Epath";

    @Test
    void 네이버_길찾기_링크는_경도가_먼저다() {
        assertCoord(parser.parse(NAVER_DIRECTIONS), 35.1820806, 126.7989859);
    }

    @Test
    void 인코딩되지_않은_길찾기_링크는_URI로_읽히지_않아_포기한다() {
        // '^'는 URI에 쓸 수 없는 문자라 URI.create가 거부한다 — 실제 페이지는 %5E로 인코딩해서
        // 내려주므로 도달하지 않는 경우다. 좌표를 잘못 만드는 대신 포기하는 쪽이 맞다.
        String raw = "https://m.search.naver.com/search.naver?nso_path=code^1301934134"
                + ";longitude^126.7989859;latitude^35.1820806";

        assertThat(parser.parse(raw)).isEmpty();
    }

    @Test
    void 좌표가_없는_네이버_검색_URL은_무시한다() {
        assertThat(parser.parse("https://m.search.naver.com/search.naver?query=%EB%A7%9B%EC%A7%91"))
                .isEmpty();
        assertThat(parser.parse("https://search.naver.com/search.naver?where=nexearch&y=10&x=20"))
                .isEmpty();
    }

    @Test
    void 네이버_지도_장소_링크_자체에는_좌표가_없다() {
        // 정규화가 모바일 장소 페이지로 보내고 그 페이지의 길찾기 링크에서 좌표를 얻는다.
        assertThat(parser.parse("https://map.naver.com/p/entry/place/1301934134?placePath=%2Fhome"))
                .isEmpty();
        assertThat(parser.parse("https://naver.me/GzE9COFR")).isEmpty();
    }

    @Test
    void 지도가_아닌_pstatic_이미지는_무시한다() {
        // 같은 CDN이 블로그 사진도 서비스한다. 경로로 갈라야 한다.
        assertThat(parser.parse("https://blogthumb.pstatic.net/food.jpg?type=w800")).isEmpty();
        assertThat(parser.parse("https://simg.pstatic.net/image/thumb.jpg?pos%3A126.82%2035.19"))
                .isEmpty();
    }

    @Test
    void 네이버_c_튜플은_지원하지_않는다() {
        // 포맷이 버전마다 달라 위도·경도를 뒤바꿀 위험이 커서 의도적으로 제외했다.
        assertThat(parser.parse("https://map.naver.com/p/entry/place/1234567?c=127.0561,37.5445,15,0,0,0,dh"))
                .isEmpty();
    }

    @Test
    void 국내_지도_링크의_국외_좌표는_거부한다() {
        // 패턴이 잘못 잡았다는 신호다. 자동으로 뒤바꾸지 않고 버린다.
        assertThat(parser.parse("https://map.naver.com/v5/search/x?lat=127.0561&lng=37.5445")).isEmpty();
    }

    // ---------- 호스트 게이트 ----------

    @Test
    void 지도가_아닌_호스트는_좌표처럼_보여도_무시한다() {
        assertThat(parser.parse("https://blog.naver.com/someone/123?q=37.5445,127.0561")).isEmpty();
        assertThat(parser.parse("https://example.com/?lat=37.5445&lng=127.0561")).isEmpty();
        assertThat(parser.parse("https://www.google.com/search?q=37.5445,127.0561")).isEmpty();
    }

    // ---------- 후보 URL / 방어 ----------

    @Test
    void 후보_URL들을_순서대로_보고_첫_성공을_쓴다() {
        // 정규화 과정에서 fragment·파라미터가 날아가므로 원본과 최종 URL을 함께 넘긴다.
        Optional<GeoPoint> result = parser.parse(
                "https://naver.me/abcd1234",                       // 단축 링크 — 좌표 없음
                "https://blog.naver.com/x/1",                      // 지도 아님
                "https://map.naver.com/v5/x?lat=37.5445&lng=127.0561");   // 해소된 최종 URL

        assertCoord(result, 37.5445, 127.0561);
    }

    @Test
    void 좌표가_0_0이면_거부한다() {
        assertThat(parser.parse("https://map.kakao.com/link/map/x,0.0,0.0")).isEmpty();
    }

    @Test
    void 범위를_벗어난_좌표는_거부한다() {
        assertThat(parser.parse("https://www.google.com/maps/@91.5,127.0561,15z")).isEmpty();
        assertThat(parser.parse("https://www.google.com/maps/@37.5445,181.5,15z")).isEmpty();
    }

    @Test
    void null과_빈_입력을_견딘다() {
        assertThat(parser.parse((String[]) null)).isEmpty();
        assertThat(parser.parse()).isEmpty();
        assertThat(parser.parse((String) null)).isEmpty();
        assertThat(parser.parse("", "   ")).isEmpty();
        assertThat(parser.parse("not a url at all")).isEmpty();
        assertThat(parser.parse("http://")).isEmpty();
    }
}
