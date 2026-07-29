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
