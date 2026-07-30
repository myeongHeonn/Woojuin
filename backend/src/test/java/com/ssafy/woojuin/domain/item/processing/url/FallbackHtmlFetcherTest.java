package com.ssafy.woojuin.domain.item.processing.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * 폴백 오케스트레이션만 검증한다. HtmlFetcher가 함수형 인터페이스라 실제 네트워크
 * 없이 람다로 각 페처를 흉내 낸다.
 */
class FallbackHtmlFetcherTest {

    private static final String URL = "https://example.com/a";

    /** readability가 기사로 인식할 만큼의 본문. 폴백 기준이 '본문 추출 성공'이므로 실물이 필요하다. */
    private static final String ARTICLE_BODY = "<article><h1>제목</h1>"
            + "<p>이 글은 폴백 판정을 위해 충분한 길이의 본문을 담고 있습니다. readability가"
            + " 기사 본문으로 인식하려면 문단이 어느 정도 길어야 하므로 의미 있는 문장을"
            + " 여러 개 둡니다. 정적 HTML만으로 본문이 확보되는 정상 페이지를 흉내 냅니다.</p>"
            + "<p>두 번째 문단입니다. 임계치(200자)를 넉넉히 넘기도록 서술을 이어갑니다."
            + " 이렇게 하면 ContentExtractor가 null이 아닌 값을 돌려주고, 폴백이 걸리지"
            + " 않는 것이 정상 동작입니다.</p></article>";

    private Document richDoc() {
        return Jsoup.parse("<html><head><meta property=\"og:title\" content=\"T\">"
                + "</head><body>" + ARTICLE_BODY + "</body></html>", URL);
    }

    private Document emptyShell() {
        // 본문이 JS로 렌더되는 SPA 껍데기 → 본문 추출 실패 → 폴백 대상.
        return Jsoup.parse("<html><head></head><body><div id=\"root\"></div></body></html>", URL);
    }

    /** og:title은 있지만 본문이 없는 SPA. 예전 기준(og:title)으로는 폴백을 놓쳤던 형태. */
    private Document previewOnlyShell() {
        return Jsoup.parse("<html><head><meta property=\"og:title\" content=\"기술블로그\">"
                + "</head><body><div id=\"root\">로딩중</div></body></html>", URL);
    }

    private FallbackHtmlFetcher fetcher(HtmlFetcher jsoup, HtmlFetcher crawler, boolean enabled) {
        return new FallbackHtmlFetcher(
                jsoup, crawler, new UrlNormalizer(), new ContentExtractor(200), enabled);
    }

    @Test
    void jsoup_결과가_충분하면_크롤러를_부르지_않는다() {
        AtomicInteger crawlerCalls = new AtomicInteger();
        HtmlFetcher jsoup = url -> richDoc();
        HtmlFetcher crawler = url -> {
            crawlerCalls.incrementAndGet();
            return richDoc();
        };

        Document result = fetcher(jsoup, crawler, true).fetch(URL);

        assertThat(result.selectFirst("meta[property=og:title]")).isNotNull();
        assertThat(crawlerCalls.get()).isZero();
    }

    @Test
    void jsoup_결과가_빈약하면_크롤러로_폴백한다() {
        AtomicInteger crawlerCalls = new AtomicInteger();
        HtmlFetcher jsoup = url -> emptyShell();
        HtmlFetcher crawler = url -> {
            crawlerCalls.incrementAndGet();
            return Jsoup.parse("<html><body><p>크롤러가 렌더한 본문</p></body></html>", URL);
        };

        Document result = fetcher(jsoup, crawler, true).fetch(URL);

        assertThat(crawlerCalls.get()).isEqualTo(1);
        assertThat(result.body().text()).contains("크롤러가 렌더한 본문");
    }

    @Test
    void og_title이_있어도_본문이_없으면_폴백한다() {
        // 우아한형제들·토스 같은 SPA가 이 형태다. 예전 기준(og:title이 있으면 충분)으로는
        // 폴백을 놓쳐 본문이 영원히 안 나왔다 — 실측으로 확인하고 기준을 바꿨다.
        AtomicInteger crawlerCalls = new AtomicInteger();
        HtmlFetcher jsoup = url -> previewOnlyShell();
        HtmlFetcher crawler = url -> {
            crawlerCalls.incrementAndGet();
            return richDoc();
        };

        Document result = fetcher(jsoup, crawler, true).fetch(URL);

        assertThat(crawlerCalls.get()).isEqualTo(1);
        assertThat(result.selectFirst("article")).isNotNull();
    }

    @Test
    void 폴백해도_정적_HTML의_메타를_유지한다() {
        // 카카오 장소 페이지 실측 사례 — 원본 twitter:image에는 좌표를 담은 스태틱맵 URL이
        // 있는데 페이지 JS가 리뷰 사진으로 덮어쓴다. 렌더 결과를 통째로 쓰면 지도 좌표를 잃는다.
        Document staticDoc = Jsoup.parse("<html><head>"
                + "<meta property=\"og:title\" content=\"한마음정육식당\">"
                + "<meta name=\"twitter:image\" content=\"http://staticmap.kakao.com/staticmap/og"
                + "?srs=wgs84&m=126.796,35.180\">"
                + "</head><body><div id=\"root\"></div></body></html>", URL);
        Document rendered = Jsoup.parse("<html><head>"
                + "<meta property=\"og:title\" content=\"한마음정육식당\">"
                + "<meta name=\"twitter:image\" content=\"//img1.kakaocdn.net/review/photo.jpg\">"
                + "</head><body>" + ARTICLE_BODY + "</body></html>", URL);

        Document result = fetcher(url -> staticDoc, url -> rendered, true).fetch(URL);

        // 좌표 소스(스태틱맵)가 살아있고
        assertThat(result.selectFirst("meta[name='twitter:image']").attr("content"))
                .contains("staticmap.kakao.com");
        // 본문은 렌더된 쪽을 쓴다
        assertThat(result.selectFirst("article")).isNotNull();
    }

    @Test
    void 정적_HTML에_미리보기가_없었으면_렌더_결과를_그대로_쓴다() {
        // 네이버 D2처럼 og:title조차 없는 경우 — 이때는 렌더 쪽 head가 더 낫다.
        Document rendered = Jsoup.parse("<html><head>"
                + "<meta property=\"og:title\" content=\"렌더로 생긴 제목\">"
                + "</head><body>" + ARTICLE_BODY + "</body></html>", URL);

        Document result = fetcher(url -> emptyShell(), url -> rendered, true).fetch(URL);

        assertThat(result.selectFirst("meta[property=og:title]").attr("content"))
                .isEqualTo("렌더로 생긴 제목");
    }

    @Test
    void jsoup이_예외로_실패하면_크롤러로_폴백한다() {
        HtmlFetcher jsoup = url -> {
            throw new HtmlFetchException("403 차단");
        };
        HtmlFetcher crawler = url -> Jsoup.parse("<html><body><p>우회 성공</p></body></html>", URL);

        Document result = fetcher(jsoup, crawler, true).fetch(URL);

        assertThat(result.body().text()).contains("우회 성공");
    }

    @Test
    void 크롤러도_실패하면_jsoup이_받아둔_문서를_그대로_쓴다() {
        HtmlFetcher jsoup = url -> emptyShell();
        HtmlFetcher crawler = url -> {
            throw new HtmlFetchException("크롤러 다운");
        };

        Document result = fetcher(jsoup, crawler, true).fetch(URL);

        // 빈약하더라도 있는 걸 준다(best-effort).
        assertThat(result.selectFirst("#root")).isNotNull();
    }

    @Test
    void jsoup도_크롤러도_실패하면_원래_jsoup_예외를_던진다() {
        HtmlFetcher jsoup = url -> {
            throw new HtmlFetchException("원본 실패");
        };
        HtmlFetcher crawler = url -> {
            throw new HtmlFetchException("폴백도 실패");
        };

        assertThatThrownBy(() -> fetcher(jsoup, crawler, true).fetch(URL))
                .isInstanceOf(HtmlFetchException.class)
                .hasMessageContaining("원본 실패");
    }

    // ---------- 정규화가 더 나은 URL을 아는 경우 ----------

    @Test
    void 정규화가_다른_호스트를_가리키면_브라우저를_굽지_않는다() {
        // 네이버 지도 앱 공유 링크가 풀리는 SPA 껍데기. 빈약하지만 크롤러로 렌더해도 장소
        // 정보가 없고, 호출부가 곧 m.place.naver.com 으로 다시 받아온다 — 수 초를 아낀다.
        String shellUrl = "https://map.naver.com/p/entry/place/1301934134";
        AtomicInteger crawlerCalls = new AtomicInteger();
        HtmlFetcher jsoup = url -> Jsoup.parse(
                "<html><head></head><body><div id=\"root\"></div></body></html>", shellUrl);
        HtmlFetcher crawler = url -> {
            crawlerCalls.incrementAndGet();
            return richDoc();
        };

        Document result = fetcher(jsoup, crawler, true).fetch(shellUrl);

        assertThat(crawlerCalls.get()).isZero();
        assertThat(result.selectFirst("#root")).isNotNull();   // 빈약한 문서를 그대로 넘긴다
    }

    @Test
    void 퍼센트_인코딩만_달라지는_URL은_폴백을_막지_않는다() {
        // UrlNormalizer가 인코딩된 경로를 디코딩하는 성질이 있어 문자열 비교로 판단하면
        // 한국어 경로 URL이 전부 '달라졌다'로 걸려 크롤러가 조용히 꺼진다. 호스트로 본다.
        String encoded = "https://spa.example.com/%EA%B8%80/1";
        AtomicInteger crawlerCalls = new AtomicInteger();
        HtmlFetcher jsoup = url -> Jsoup.parse(
                "<html><head></head><body><div id=\"root\"></div></body></html>", encoded);
        HtmlFetcher crawler = url -> {
            crawlerCalls.incrementAndGet();
            return Jsoup.parse("<html><body><p>렌더된 본문</p></body></html>", encoded);
        };

        Document result = fetcher(jsoup, crawler, true).fetch(encoded);

        assertThat(crawlerCalls.get()).isEqualTo(1);
        assertThat(result.body().text()).contains("렌더된 본문");
    }

    @Test
    void 같은_호스트로_정규화되면_폴백한다() {
        // 추적 파라미터만 떨어지는 경우 — 다른 페이지가 아니므로 크롤러가 필요하다.
        String tracked = "https://spa.example.com/post?id=1&utm_source=x";
        AtomicInteger crawlerCalls = new AtomicInteger();
        HtmlFetcher jsoup = url -> Jsoup.parse(
                "<html><head></head><body><div id=\"root\"></div></body></html>", tracked);
        HtmlFetcher crawler = url -> {
            crawlerCalls.incrementAndGet();
            return Jsoup.parse("<html><body><p>렌더된 본문</p></body></html>", tracked);
        };

        fetcher(jsoup, crawler, true).fetch(tracked);

        assertThat(crawlerCalls.get()).isEqualTo(1);
    }

    @Test
    void 크롤러가_꺼져있으면_빈약해도_폴백하지_않는다() {
        AtomicInteger crawlerCalls = new AtomicInteger();
        HtmlFetcher jsoup = url -> emptyShell();
        HtmlFetcher crawler = url -> {
            crawlerCalls.incrementAndGet();
            return richDoc();
        };

        Document result = fetcher(jsoup, crawler, false).fetch(URL);

        assertThat(crawlerCalls.get()).isZero();
        assertThat(result.selectFirst("#root")).isNotNull();
    }

    @Test
    void 크롤러가_꺼져있고_jsoup이_실패하면_그대로_예외를_던진다() {
        HtmlFetcher jsoup = url -> {
            throw new HtmlFetchException("꺼진채 실패");
        };
        HtmlFetcher crawler = url -> richDoc();

        assertThatThrownBy(() -> fetcher(jsoup, crawler, false).fetch(URL))
                .isInstanceOf(HtmlFetchException.class)
                .hasMessageContaining("꺼진채 실패");
    }
}
