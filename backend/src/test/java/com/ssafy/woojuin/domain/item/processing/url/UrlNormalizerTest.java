package com.ssafy.woojuin.domain.item.processing.url;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlNormalizerTest {

    private final UrlNormalizer normalizer = new UrlNormalizer();

    @Test
    void utm_추적_파라미터를_제거한다() {
        String result = normalizer.normalize(
                "https://example.com/post?id=10&utm_source=news&utm_medium=email");

        assertThat(result).isEqualTo("https://example.com/post?id=10");
    }

    @Test
    void 알려진_추적_키와_fragment를_제거한다() {
        String result = normalizer.normalize("https://example.com/a?fbclid=xyz#section");

        assertThat(result).isEqualTo("https://example.com/a");
    }

    @Test
    void youtu_be_단축링크를_watch_형태로_바꾼다() {
        String result = normalizer.normalize("https://youtu.be/dQw4w9WgXcQ");

        assertThat(result).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    // ---------- 네이버 블로그 (모바일 호스트로 보낸다) ----------

    /**
     * 데스크톱 포스트 URL은 2.8KB iframe 껍데기라 본문도 OG 태그도 없다 — 실측으로 확인했고
     * UrlNormalizer javadoc에 표로 남겼다. 모바일은 같은 글의 본문·OG·썸네일을 다 준다.
     */
    @Test
    void 네이버블로그_데스크톱_포스트를_모바일로_바꾼다() {
        String result = normalizer.normalize("https://blog.naver.com/woojuin/223");

        assertThat(result).isEqualTo("https://m.blog.naver.com/woojuin/223");
    }

    @Test
    void 네이버블로그_iframe_PostView도_모바일_포스트로_바꾼다() {
        String result = normalizer.normalize(
                "https://blog.naver.com/PostView.naver?blogId=woojuin&logNo=223");

        assertThat(result).isEqualTo("https://m.blog.naver.com/woojuin/223");
    }

    @Test
    void 네이버블로그_모바일_링크는_모바일로_유지한다() {
        // 일반 m.* → 데스크톱 규칙이 먼저 걸리면 본문 없는 껍데기로 되돌아간다.
        String result = normalizer.normalize("https://m.blog.naver.com/woojuin/223");

        assertThat(result).isEqualTo("https://m.blog.naver.com/woojuin/223");
    }

    @Test
    void 네이버블로그_추적파라미터가_붙어도_글만_특정한다() {
        String result = normalizer.normalize(
                "https://blog.naver.com/woojuin/223?trackingCode=blog_bloghome&utm_source=x");

        assertThat(result).isEqualTo("https://m.blog.naver.com/woojuin/223");
    }

    @Test
    void 네이버블로그_글을_특정할수_없으면_손대지_않는다() {
        // 블로그 홈(logNo 없음)은 바꿔서 나아질 게 없다.
        assertThat(normalizer.normalize("https://blog.naver.com/woojuin"))
                .isEqualTo("https://blog.naver.com/woojuin");
        // logNo가 숫자가 아니면 글 URL이 아니다.
        assertThat(normalizer.normalize("https://blog.naver.com/woojuin/PostList.naver"))
                .isEqualTo("https://blog.naver.com/woojuin/PostList.naver");
        // PostView인데 파라미터가 없는 경우.
        assertThat(normalizer.normalize("https://blog.naver.com/PostView.naver"))
                .isEqualTo("https://blog.naver.com/PostView.naver");
    }

    // ---------- 카카오 장소 (앱링크 랜딩 → 장소 페이지) ----------

    /**
     * 카카오톡에서 공유한 지도 링크(kko.to)는 앱 설치를 유도하는 랜딩 페이지로 풀린다.
     * 그 페이지는 og:title이 "카카오맵"인 안내 페이지(robots noindex)라 장소 정보가 없다.
     * 장소 페이지는 같은 장소의 스태틱맵 좌표와 이름·주소를 담는다.
     */
    @Test
    void 카카오_앱링크_랜딩을_장소페이지로_바꾼다() {
        String result = normalizer.normalize(
                "https://applink.map.kakao.com/place?id=1208117084");

        assertThat(result).isEqualTo("https://place.map.kakao.com/1208117084");
    }

    @Test
    void 카카오_앱링크에_장소id가_없으면_손대지_않는다() {
        assertThat(normalizer.normalize("https://applink.map.kakao.com/place"))
                .isEqualTo("https://applink.map.kakao.com/place");
        assertThat(normalizer.normalize("https://applink.map.kakao.com/place?id=abc"))
                .isEqualTo("https://applink.map.kakao.com/place?id=abc");
    }

    @Test
    void 좌표가_박힌_카카오_공유링크는_건드리지_않는다() {
        // /link/map 형태는 URL 자체에 좌표가 있어 그대로 두는 게 맞다.
        String url = "https://map.kakao.com/link/map/cafe,37.5445,127.0561";

        assertThat(normalizer.normalize(url)).isEqualTo(url);
    }

    // ---------- 네이버 장소 (모바일 장소 페이지로 모은다) ----------

    /**
     * 지도 링크는 URL에 좌표가 없고 페이지도 2.3KB SPA 껍데기라 미리보기도 좌표도 못 얻는다.
     * 모바일 장소 페이지는 같은 장소를 580KB로 주면서 og:title·썸네일과 좌표를 담은
     * '길찾기' 링크를 함께 싣는다.
     */
    @Test
    void 네이버_지도_장소링크를_모바일_장소페이지로_바꾼다() {
        String result = normalizer.normalize(
                "https://map.naver.com/p/entry/place/1301934134?placePath=%2Fhome");

        assertThat(result).isEqualTo("https://m.place.naver.com/place/1301934134");
    }

    @Test
    void 네이버_플레이스_카테고리_경로도_장소페이지로_모은다() {
        assertThat(normalizer.normalize("https://m.place.naver.com/restaurant/1301934134/home"))
                .isEqualTo("https://m.place.naver.com/place/1301934134");
        assertThat(normalizer.normalize("https://place.naver.com/restaurant/1301934134/home"))
                .isEqualTo("https://m.place.naver.com/place/1301934134");
    }

    @Test
    void 네이버_플레이스_모바일_링크는_데스크톱으로_되돌리지_않는다() {
        // 일반 m.* → 데스크톱 규칙이 먼저 걸리면 place.naver.com이 되어 버린다.
        assertThat(normalizer.normalize("https://m.place.naver.com/place/1301934134"))
                .isEqualTo("https://m.place.naver.com/place/1301934134");
    }

    @Test
    void 네이버_지도에서_장소를_특정할수_없으면_손대지_않는다() {
        // 지도 검색 화면·좌표 보기 등은 특정 장소가 아니다. 호스트도 경로도 그대로 남는다.
        assertThat(normalizer.normalize("https://map.naver.com/p/search/cafe"))
                .isEqualTo("https://map.naver.com/p/search/cafe");
        assertThat(normalizer.normalize("https://map.naver.com/p/favorite/place"))
                .isEqualTo("https://map.naver.com/p/favorite/place");
    }

    // ---------- 일반 규칙 ----------

    @Test
    void 모바일_호스트를_데스크톱으로_바꾼다() {
        // 네이버 블로그만 예외이고 나머지는 그대로 데스크톱으로 보낸다.
        String result = normalizer.normalize("https://m.example.com/path?q=1");

        assertThat(result).isEqualTo("https://example.com/path?q=1");
    }

    @Test
    void 정규화할것이_없으면_원본을_유지한다() {
        String url = "https://example.com/clean";
        assertThat(normalizer.normalize(url)).isEqualTo(url);
    }

    @Test
    void 파싱_불가능한_입력은_원본을_그대로_반환한다() {
        String bad = "not a url ::: %%%";
        assertThat(normalizer.normalize(bad)).isEqualTo(bad);
    }
}
