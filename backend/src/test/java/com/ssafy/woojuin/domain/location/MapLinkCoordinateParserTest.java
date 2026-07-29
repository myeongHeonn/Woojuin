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
