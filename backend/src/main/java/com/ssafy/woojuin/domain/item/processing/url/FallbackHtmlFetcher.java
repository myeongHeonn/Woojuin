package com.ssafy.woojuin.domain.item.processing.url;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 두 페처를 엮는 기본 {@link HtmlFetcher}. 빠른 경로로 Jsoup을 먼저 쓰고, 그 결과가
 * 쓸모없을 때만 무거운 Scrapling 크롤러로 폴백한다. {@code @Primary}라 이 도메인의
 * 모든 소비자({@code UrlItemProcessor}·{@code UrlBatchExportRunner})가 별도 수정
 * 없이 이 체인을 주입받는다.
 *
 * <p><b>폴백 트리거</b> (둘 중 하나):
 * <ul>
 *   <li>Jsoup이 예외로 실패 — 403/503 봇 차단, 타임아웃 등</li>
 *   <li>Jsoup은 200을 받았지만 <b>본문이 안 나옴</b> — 판정을 {@link ContentExtractor}에
 *       위임한다. 트랙 B의 성공 조건과 같은 기준이라 "본문이 필요한데 없다"가 곧 폴백
 *       조건이 되고, 임계치가 두 곳에 흩어지지 않는다.</li>
 * </ul>
 *
 * <p>판정 기준을 og:title·body 텍스트 길이에서 본문 추출로 바꾼 이유는 실측 때문이다.
 * 둘 다 이 문제를 못 가른다.
 * <pre>
 *   techblog.woowahan.com   og:title 있음, body 2101자 → 본문 추출 null   ← 폴백 필요
 *   place.map.kakao.com     og:title 있음, body    0자 → 본문 추출 null
 * </pre>
 * og:title로 판단하면 앞의 것을 놓치고(SPA 본문이 영원히 안 나온다), body 길이로 판단하면
 * 순서가 뒤집힌다. 실제로 우아한형제들은 크롤러가 렌더하면 본문이 3349자 나온다.
 *
 * <p><b>렌더 결과가 메타를 망칠 수 있어 head는 정적 HTML 쪽을 쓴다.</b> 카카오 장소 페이지는
 * 원본 HTML의 {@code twitter:image}에 좌표를 담은 스태틱맵 URL을 두는데, 페이지 JS가 로드 후
 * 그 값을 리뷰 사진으로 <b>덮어쓴다</b>. 렌더 결과를 통째로 쓰면 지도 좌표를 잃는다(실측으로
 * 확인했다 — 이 폴백을 켜자 카카오 장소 아이템의 lat/lng이 사라졌다).
 *
 * <pre>
 *   원본 HTML   twitter:image = staticmap.kakao.com/...&amp;m=126.796,35.180   ← 좌표
 *   렌더 후      twitter:image = img1.kakaocdn.net/cthumb/...                 ← 리뷰 사진
 * </pre>
 *
 * <p>그래서 정적 HTML에 이미 미리보기가 있으면(og:title 존재) <b>그 head를 유지</b>하고 렌더된
 * body만 취한다 — 메타는 서버가 준 게 정확하고 본문은 브라우저가 그린 게 정확하다. 정적
 * HTML에 미리보기가 아예 없었으면 렌더 결과를 그대로 쓴다(그쪽이 더 나은 유일한 경우다).
 *
 * <p>대가: 본문이 원래 없는 페이지(장소 페이지 등)도 폴백을 한 번 타서 수 초를 쓴다.
 * 문제가 되면 {@code woojuin.crawler.enabled=false}로 전체를 끌 수 있다.
 *
 * <p><b>실패 처리</b>: 크롤러 폴백까지 실패해도, Jsoup이 그나마 문서를 받아뒀다면
 * 그걸(빈약하더라도) 돌려준다 — best-effort. Jsoup·크롤러가 모두 실패했을 때만
 * 원래 Jsoup 예외를 던진다. 크롤러가 꺼져 있으면({@code woojuin.crawler.enabled=false})
 * 폴백을 아예 시도하지 않고 Jsoup 동작을 그대로 노출한다.
 *
 * <p><b>정규화가 더 나은 URL을 아는 경우엔 폴백하지 않는다.</b> 지도 앱 공유 링크는
 * 앱 설치 유도 페이지로 풀리는데(네이버 {@code map.naver.com/p/entry/place/{id}}는 2.3KB SPA
 * 껍데기라 og:title도 없다) 이건 정확히 "빈약함" 조건에 걸린다. 그대로 두면 <b>버릴 페이지를
 * 브라우저로 굽는 데 수 초</b>를 쓰고, 그 직후 {@link UrlItemProcessor}가 최종 URL을 재정규화해
 * 장소 페이지를 다시 받아온다. 호출부가 {@code @Transactional}이라 그 낭비가 트랜잭션 체류
 * 시간으로 그대로 들어온다.
 *
 * <p>그래서 재정규화 결과가 <b>다른 호스트</b>를 가리키면 크롤러를 건너뛰고 빈약한 문서를
 * 그대로 넘긴다 — 호출부가 더 나은 URL로 다시 받아오고, 그 페이지가 그때도 빈약하면 거기서
 * 크롤러가 돈다. 문자열이 달라졌는지가 아니라 <b>호스트</b>가 달라졌는지로 보는 이유는
 * {@code UrlNormalizer.normalize}가 퍼센트 인코딩된 경로를 디코딩하는 성질이 있어서다 —
 * 문자열 비교로 판단하면 한국어 경로를 가진 URL이 전부 "달라졌다"로 걸려 크롤러가 꺼진다.
 */
@Slf4j
@Primary
@Component
public class FallbackHtmlFetcher implements HtmlFetcher {

    private final HtmlFetcher jsoup;
    private final HtmlFetcher crawler;
    private final UrlNormalizer urlNormalizer;
    private final ContentExtractor contentExtractor;
    private final boolean crawlerEnabled;

    public FallbackHtmlFetcher(
            @Qualifier("jsoupHtmlFetcher") HtmlFetcher jsoup,
            @Qualifier("scraplingHtmlFetcher") HtmlFetcher crawler,
            UrlNormalizer urlNormalizer,
            ContentExtractor contentExtractor,
            @Value("${woojuin.crawler.enabled:false}") boolean crawlerEnabled) {
        this.jsoup = jsoup;
        this.crawler = crawler;
        this.urlNormalizer = urlNormalizer;
        this.contentExtractor = contentExtractor;
        this.crawlerEnabled = crawlerEnabled;
    }

    @Override
    public Document fetch(String url) {
        Document jsoupDoc = null;
        HtmlFetchException jsoupError = null;
        try {
            jsoupDoc = jsoup.fetch(url);
            if (!crawlerEnabled || looksSufficient(jsoupDoc)) {
                return jsoupDoc;
            }
            String better = urlNormalizer.betterUrlOnAnotherHost(jsoupDoc.location()).orElse(null);
            if (better != null) {
                // 브라우저를 굽기 전에 호출부가 더 나은 URL로 다시 받아오게 둔다.
                log.info("빈약하지만 정규화가 더 나은 URL을 알고 있어 크롤러를 건너뛴다: {} → {}",
                        jsoupDoc.location(), better);
                return jsoupDoc;
            }
            log.info("Jsoup 결과가 빈약함, 스텔스 크롤러로 폴백: url={}", url);
        } catch (HtmlFetchException e) {
            if (!crawlerEnabled) {
                throw e;
            }
            jsoupError = e;
            log.info("Jsoup fetch 실패, 스텔스 크롤러로 폴백: url={}, cause={}", url, e.getMessage());
        }

        try {
            return preferStaticHead(jsoupDoc, crawler.fetch(url));
        } catch (HtmlFetchException e) {
            if (jsoupDoc != null) {
                // 크롤러가 못 뚫어도 Jsoup이 받아둔 빈약한 문서라도 쓴다.
                log.info("크롤러 폴백도 실패, Jsoup 결과 사용: url={}, cause={}", url, e.getMessage());
                return jsoupDoc;
            }
            log.info("크롤러 폴백 실패(Jsoup도 실패): url={}, cause={}", url, e.getMessage());
            throw (jsoupError != null) ? jsoupError : e;
        }
    }

    /**
     * 렌더된 문서를 쓰되, 정적 HTML에 이미 미리보기가 있었다면 그 {@code <head>}를 유지한다.
     * 이유는 클래스 javadoc의 카카오 스태틱맵 사례 — 페이지 JS가 메타를 덮어써서 렌더 결과의
     * head는 좌표를 잃는다.
     */
    private Document preferStaticHead(Document staticDoc, Document rendered) {
        if (staticDoc == null || staticDoc.selectFirst("meta[property=og:title]") == null) {
            return rendered;   // 정적 HTML에 미리보기가 없었으면 렌더 쪽이 낫다
        }
        Element renderedHead = rendered.head();
        if (renderedHead == null || staticDoc.head() == null) {
            return rendered;
        }
        renderedHead.replaceWith(staticDoc.head().clone());
        log.info("렌더된 본문 + 정적 HTML의 head를 함께 쓴다(메타 덮어쓰기 방지): url={}",
                rendered.location());
        return rendered;
    }

    /**
     * 정적 HTML만으로 본문이 나오는지. 판정을 {@link ContentExtractor}에 맡겨 트랙 B와 같은
     * 기준을 쓴다 — 클래스 javadoc의 실측 표 참고.
     *
     * <p>readability는 문서를 복제해서 다루므로 여기서 한 번 더 돌려도 원본이 변형되지 않는다.
     * CPU만 쓰는 작업이라 폴백 여부를 정하는 값으로는 충분히 싸다.
     */
    private boolean looksSufficient(Document doc) {
        return contentExtractor.extract(doc) != null;
    }
}
