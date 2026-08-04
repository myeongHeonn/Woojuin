package com.ssafy.woojuin.domain.item.processing.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.ai.AiAnalysis;
import com.ssafy.woojuin.domain.ai.AiAnalyzer;
import com.ssafy.woojuin.domain.category.service.CategoryAssignmentService;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.event.ItemDoneEvent;
import com.ssafy.woojuin.domain.item.processing.ItemProcessingMessage;
import com.ssafy.woojuin.domain.location.Geocoder;
import com.ssafy.woojuin.domain.location.LocationResolver;
import com.ssafy.woojuin.domain.location.MapLinkCoordinateParser;
import com.ssafy.woojuin.global.common.ItemStatus;
import com.ssafy.woojuin.global.common.TransactionRunner;
import java.util.List;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 오케스트레이션 + 상태 전이 검증. 각 조각은 mock으로 대체하고, 어떤 조합에서 DONE/
 * PARTIAL/FAILED가 나오는지에 집중한다. AI 보강은 본문 유무와 무관하게 항상 호출된다.
 */
@ExtendWith(MockitoExtension.class)
class UrlItemProcessorTest {

    @Mock ItemRepository itemRepository;
    @Mock OEmbedClient oEmbedClient;
    @Mock HtmlFetcher htmlFetcher;
    @Mock OpenGraphScraper openGraphScraper;
    @Mock ContentExtractor contentExtractor;
    @Mock AiAnalyzer aiAnalyzer;
    @Mock CategoryAssignmentService categoryAssignmentService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock Geocoder geocoder;

    UrlItemProcessor processor;

    private final Document doc = Jsoup.parse("<html></html>", "https://example.com/a");

    @BeforeEach
    void setUp() {
        // 정규화는 입력을 그대로 돌려주도록 둔다(이 테스트의 관심사가 아님).
        UrlNormalizer normalizer = new UrlNormalizer() {
            @Override
            public String normalize(String rawUrl) {
                return rawUrl;
            }
        };
        // 파서는 순수 함수라 실제 구현을 쓰고 외부 호출이 필요한 지오코더만 목으로 둔다.
        LocationResolver locationResolver = new LocationResolver(
                new MapLinkCoordinateParser(), geocoder);
        // 단위 테스트에선 프록시가 없어 람다가 트랜잭션 없이 인라인 실행된다(TransactionRunner javadoc).
        processor = new UrlItemProcessor(itemRepository, normalizer, oEmbedClient,
                htmlFetcher, openGraphScraper, contentExtractor, aiAnalyzer, categoryAssignmentService,
                eventPublisher, locationResolver, new TransactionRunner());
    }

    private Item urlItem() {
        return urlItem("https://example.com/a");
    }

    private Item urlItem(String url) {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.URL)
                .url(url).build();
        when(itemRepository.findById(any())).thenReturn(Optional.of(item));
        return item;
    }

    private ItemProcessingMessage message() {
        return new ItemProcessingMessage(1L, 1L, ItemType.URL);
    }

    /** load 이후엔 enrichWithAi가 항상 도므로 analyzer는 기본으로 empty를 돌려주게 둔다. */
    private void aiReturnsEmpty() {
        when(aiAnalyzer.analyze(any())).thenReturn(AiAnalysis.empty());
    }

    @Test
    void 트랙B_성공하면_본문저장하고_DONE() {
        Item item = urlItem();
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("제목", "https://img", "설명"));
        when(contentExtractor.extract(doc)).thenReturn("충분히 긴 본문 텍스트");

        processor.process(message());

        assertThat(item.getStatus()).isEqualTo(ItemStatus.DONE);
        assertThat(item.getTitle()).isEqualTo("제목");
        assertThat(item.getContent()).isEqualTo("충분히 긴 본문 텍스트");
    }

    @Test
    void 트랙A만_성공하면_PARTIAL이고_AI는_본문없이_호출된다() {
        Item item = urlItem();
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("제목", null, null));
        when(contentExtractor.extract(doc)).thenReturn(null);   // 트랙 B 실패

        processor.process(message());

        assertThat(item.getStatus()).isEqualTo(ItemStatus.PARTIAL);
        assertThat(item.getTitle()).isEqualTo("제목");
        assertThat(item.getContent()).isNull();
        // PARTIAL도 미리보기(제목)는 있으므로 알림은 보낸다.
        verify(eventPublisher).publishEvent(new ItemDoneEvent(item.getId()));
        // 본문이 없어도 분류는 시도한다(title 기반)
        verify(categoryAssignmentService).assign(eq(item.getId()), eq(1L), any());
    }

    @Test
    void oEmbed_성공하면_본문없이_PARTIAL() {
        Item item = urlItem();
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(
                Optional.of(new UrlPreview("영상 제목", "https://thumb", "채널")));

        processor.process(message());

        assertThat(item.getStatus()).isEqualTo(ItemStatus.PARTIAL);
        assertThat(item.getTitle()).isEqualTo("영상 제목");
        assertThat(item.getPreviewThumbnailUrl()).isEqualTo("https://thumb");
        verifyNoInteractions(htmlFetcher);   // oEmbed로 끝났으면 HTML fetch 안 함
    }

    @Test
    void fetch_실패하면_URL_폴백_PARTIAL() {
        Item item = urlItem();
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenThrow(new HtmlFetchException("차단됨"));

        processor.process(message());

        assertThat(item.getStatus()).isEqualTo(ItemStatus.PARTIAL);
        assertThat(item.getTitle()).isEqualTo("example.com/a");   // 호스트 + 경로
    }

    @Test
    void 폴백_제목은_경로까지_담고_추적파라미터는_버린다() {
        // 쇼핑몰 링크를 여러 개 저장했을 때 카드가 전부 호스트명으로 보이면 구별이 안 된다.
        // 네이버 스토어는 봇 차단·레이트리밋으로 미리보기가 자주 실패하고, NaPm 추적
        // 파라미터가 수백 자라 제목에 넣으면 방해만 된다.
        Item item = urlItem("https://smartstore.naver.com/flytojapan/products/11409077567"
                + "?NaPm=ct%3Dms5t04fc%7Cci%3D738959d4f1cb55ee2f35f2b662cdba1c");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenThrow(new HtmlFetchException("429 레이트리밋"));

        processor.process(message());

        assertThat(item.getTitle())
                .isEqualTo("smartstore.naver.com/flytojapan/products/11409077567");
    }

    @Test
    void 폴백_제목은_호스트를_못_읽어도_견딘다() {
        Item item = urlItem("not a url");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenThrow(new HtmlFetchException("잘못된 URL"));

        processor.process(message());

        assertThat(item.getTitle()).isEqualTo("not a url");
        assertThat(item.getStatus()).isEqualTo(ItemStatus.PARTIAL);
    }

    @Test
    void AI가_요약과_카테고리를_주면_저장한다() {
        Item item = urlItem();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("제목", null, null));
        when(contentExtractor.extract(doc)).thenReturn("본문");
        when(aiAnalyzer.analyze(any())).thenReturn(new AiAnalysis(null, "요약문", List.of("학습·지식")));

        processor.process(message());

        assertThat(item.getSummary()).isEqualTo("요약문");
        verify(categoryAssignmentService).assign(eq(item.getId()), eq(1L), eq(List.of("학습·지식")));
    }

    @Test
    void 아이템이_사라졌으면_조용히_반환한다() {
        when(itemRepository.findById(any())).thenReturn(Optional.empty());

        processor.process(message());   // 예외 없이 통과

        verifyNoInteractions(oEmbedClient, htmlFetcher, contentExtractor, aiAnalyzer, categoryAssignmentService);
    }

    @Test
    void 이미_처리된_아이템은_재처리하지_않는다() {
        Item item = urlItem();
        item.markDone();   // at-least-once 큐 재배달 시나리오 시뮬레이션

        processor.process(message());

        verifyNoInteractions(oEmbedClient, htmlFetcher, contentExtractor, aiAnalyzer, categoryAssignmentService);
    }

    @Test
    void 외부_호출_중_아이템이_삭제되면_아무것도_반영하지_않는다() {
        // 외부 호출(크롤·LLM)이 트랜잭션 밖으로 나가면서 생기는 경합 — 반영 트랜잭션이
        // 다시 로드해 가드를 재확인해야 삭제된 아이템에 카테고리를 붙이는 사고가 없다.
        Item item = urlItem();
        aiReturnsEmpty();
        when(itemRepository.findById(any()))
                .thenReturn(Optional.of(item))    // 1단계: 스냅숏은 살아 있었다
                .thenReturn(Optional.empty());    // 3단계: 반영 시점엔 삭제됨
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("제목", null, null));
        when(contentExtractor.extract(doc)).thenReturn("본문");

        processor.process(message());   // 예외 없이 통과

        assertThat(item.getStatus()).isEqualTo(ItemStatus.PROCESSING);   // 스냅숏은 안 건드렸다
        // candidates는 2단계(외부 호출)에서 이미 불렸을 수 있으니 쓰기 경로인 assign만 본다.
        verify(categoryAssignmentService, never()).assign(any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    // ---------- 위치 확보 (FR-023) ----------

    @Test
    void 지도_공유링크는_URL에서_좌표를_얻고_주소는_역지오코딩한다() {
        Item item = urlItem("https://map.kakao.com/link/map/cafe,37.5445,127.0561");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("성수동 카페", null, null));
        when(contentExtractor.extract(doc)).thenReturn("본문");
        when(geocoder.reverse(any())).thenReturn(Optional.of("서울특별시 성동구 아차산로 100"));

        processor.process(message());

        assertThat(item.getLat()).isEqualTo(37.5445);
        assertThat(item.getLng()).isEqualTo(127.0561);
        assertThat(item.getAddress()).isEqualTo("서울특별시 성동구 아차산로 100");
    }

    @Test
    void 지도_공유링크의_역지오코딩이_실패해도_좌표는_저장된다() {
        Item item = urlItem("https://map.kakao.com/link/map/cafe,37.5445,127.0561");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("성수동 카페", null, null));
        when(contentExtractor.extract(doc)).thenReturn("본문");
        when(geocoder.reverse(any())).thenReturn(Optional.empty());

        processor.process(message());

        assertThat(item.hasCoordinates()).isTrue();
        assertThat(item.getAddress()).isNull();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.DONE);
    }

    @Test
    void 본문에_임베드된_지도의_핀_좌표를_쓴다() {
        // 맛집 블로그의 실제 시나리오 — 글 URL엔 좌표가 없고 본문 지도 이미지에만 있다.
        // 지식·기술 글은 지도를 심지 않으므로 이 신호가 두 부류를 가른다.
        Item item = urlItem("https://m.blog.naver.com/someone/224131224522");
        aiReturnsEmpty();
        Document blogDoc = Jsoup.parse("""
                <html><body><p>맛있었어요</p>
                  <img src="https://blogthumb.pstatic.net/food.jpg">
                  <img src="https://simg.pstatic.net/static.map/v2/map/staticmap.bin?\
                caller=smarteditor&amp;markers=color%3A0x11cc73%7Csize%3Amid\
                %7Cpos%3A126.8234182%2035.1909497%7Ctype%3Ad&amp;w=700&amp;h=315">
                </body></html>""", "https://m.blog.naver.com/someone/224131224522");
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(blogDoc);
        when(openGraphScraper.scrape(blogDoc))
                .thenReturn(new UrlPreview("광주 수완지구 초밥", null, "안녕하세요 개구장이입니다"));
        when(contentExtractor.extract(blogDoc)).thenReturn("충분히 긴 본문");
        when(geocoder.reverse(any()))
                .thenReturn(Optional.of("전남광주통합특별시 광산구 임방울대로 347"));

        processor.process(message());

        assertThat(item.getLat()).isEqualTo(35.1909497);
        assertThat(item.getLng()).isEqualTo(126.8234182);
        assertThat(item.getAddress()).isEqualTo("전남광주통합특별시 광산구 임방울대로 347");
    }

    @Test
    void 단축링크가_해소된_뒤_정규화가_달라지면_다시_받아온다() {
        // naver.me 는 정규화 시점엔 불투명한 토큰이고, 해소된 뒤에야 지도 장소임을 알 수 있다.
        // 이게 없으면 2.3KB SPA 껍데기에서 끝나 미리보기도 좌표도 못 얻는다.
        Item item = urlItem("https://naver.me/GzE9COFR");
        aiReturnsEmpty();
        // 이 테스트는 실제 정규화 규칙을 써야 의미가 있으므로 setUp의 통과 스텁을 대체한다.
        processor = new UrlItemProcessor(itemRepository, new UrlNormalizer(), oEmbedClient,
                htmlFetcher, openGraphScraper, contentExtractor, aiAnalyzer,
                categoryAssignmentService, eventPublisher,
                new LocationResolver(new MapLinkCoordinateParser(), geocoder), new TransactionRunner());

        Document shell = Jsoup.parse("<html><head><title>네이버 지도</title></head></html>",
                "https://map.naver.com/p/entry/place/1301934134?placePath=%2Fhome");
        Document placePage = Jsoup.parse("""
                <html><body>
                  <a href="https://m.search.naver.com/search.naver?nso_path=code%5E1301934134\
                %3Blongitude%5E126.7989859%3Blatitude%5E35.1820806">길찾기</a>
                </body></html>""", "https://m.place.naver.com/place/1301934134");

        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch("https://naver.me/GzE9COFR")).thenReturn(shell);
        when(htmlFetcher.fetch("https://m.place.naver.com/place/1301934134")).thenReturn(placePage);
        when(openGraphScraper.scrape(placePage))
                .thenReturn(new UrlPreview("푸드박스 광주점 : 네이버", null, null));
        when(contentExtractor.extract(placePage)).thenReturn(null);
        when(geocoder.reverse(any()))
                .thenReturn(Optional.of("전남광주통합특별시 광산구 하남대로 100"));

        processor.process(message());

        // 두 번째 문서를 썼다 — 미리보기도 좌표도 그쪽에서 나온다.
        assertThat(item.getTitle()).isEqualTo("푸드박스 광주점 : 네이버");
        assertThat(item.getLat()).isEqualTo(35.1820806);
        assertThat(item.getLng()).isEqualTo(126.7989859);
        verify(htmlFetcher).fetch("https://m.place.naver.com/place/1301934134");
    }

    @Test
    void 재요청이_실패하면_원래_문서를_그대로_쓴다() {
        // 최적화 때문에 아이템 가공이 실패하면 안 된다.
        Item item = urlItem("https://naver.me/GzE9COFR");
        aiReturnsEmpty();
        processor = new UrlItemProcessor(itemRepository, new UrlNormalizer(), oEmbedClient,
                htmlFetcher, openGraphScraper, contentExtractor, aiAnalyzer,
                categoryAssignmentService, eventPublisher,
                new LocationResolver(new MapLinkCoordinateParser(), geocoder), new TransactionRunner());

        Document shell = Jsoup.parse("<html></html>",
                "https://map.naver.com/p/entry/place/1301934134");
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch("https://naver.me/GzE9COFR")).thenReturn(shell);
        when(htmlFetcher.fetch("https://m.place.naver.com/place/1301934134"))
                .thenThrow(new HtmlFetchException("차단됨"));
        when(openGraphScraper.scrape(shell)).thenReturn(new UrlPreview("네이버 지도", null, null));
        when(contentExtractor.extract(shell)).thenReturn(null);

        processor.process(message());

        assertThat(item.getTitle()).isEqualTo("네이버 지도");
        assertThat(item.getStatus()).isEqualTo(ItemStatus.PARTIAL);
        assertThat(item.hasCoordinates()).isFalse();
    }

    @Test
    void 본문에_주소가_적혀_있어도_지도가_없으면_위치를_만들지_않는다() {
        // 의도된 동작이다 — 자유 텍스트 주소 경로를 없앴다(LocationResolver javadoc 참고).
        // 지식·기술 글이나 회사 footer 주소에 핀이 꽂히는 것을 구조적으로 막는다.
        Item item = urlItem("https://blog.example.com/post/1");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc))
                .thenReturn(new UrlPreview("성수동 맛집", null, "서울 성동구 아차산로 49"));
        when(contentExtractor.extract(doc)).thenReturn("주소는 서울 성동구 아차산로 49 입니다");

        processor.process(message());

        assertThat(item.hasCoordinates()).isFalse();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.DONE);
        verifyNoInteractions(geocoder);
    }

    @Test
    void 위치가_없어도_상태는_평소와_같다() {
        Item item = urlItem();
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("아티클", null, null));
        when(contentExtractor.extract(doc)).thenReturn("위치와 무관한 본문");

        processor.process(message());

        assertThat(item.hasCoordinates()).isFalse();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.DONE);   // 위치 없음은 정상이다
        verifyNoInteractions(geocoder);
    }

    @Test
    void 지오코더가_예외를_던져도_상태와_본문이_유지된다() {
        // @Transactional 안에서 예외가 새면 rollback-only로 찍혀 이미 확보한 미리보기·본문이
        // 전부 버려지고, 디스패처가 RETRYABLE로 판단해 AI 호출까지 다시 돈다.
        Item item = urlItem("https://map.kakao.com/link/map/cafe,37.5445,127.0561");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc))
                .thenReturn(new UrlPreview("성수동 맛집", "https://img", null));
        when(contentExtractor.extract(doc)).thenReturn("확보한 본문");
        when(geocoder.reverse(any())).thenThrow(new RuntimeException("지오코딩 폭발"));

        processor.process(message());   // 예외가 밖으로 나오지 않아야 한다

        assertThat(item.getStatus()).isEqualTo(ItemStatus.DONE);
        assertThat(item.getContent()).isEqualTo("확보한 본문");
        assertThat(item.getPreviewThumbnailUrl()).isEqualTo("https://img");
        assertThat(item.hasCoordinates()).isFalse();
    }

    @Test
    void fetch가_실패해도_원본_URL에서_좌표를_뽑는다() {
        // doc이 null이라 doc.location()은 못 쓰지만 원본 URL만으로도 충분한 경우.
        Item item = urlItem("https://map.kakao.com/link/map/cafe,37.5445,127.0561");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenThrow(new HtmlFetchException("차단됨"));
        when(geocoder.reverse(any())).thenReturn(Optional.of("서울특별시 성동구 아차산로 100"));

        processor.process(message());

        assertThat(item.getLat()).isEqualTo(37.5445);
        assertThat(item.getAddress()).isEqualTo("서울특별시 성동구 아차산로 100");
        assertThat(item.getStatus()).isEqualTo(ItemStatus.PARTIAL);
    }

    @Test
    void 카카오_장소페이지는_스태틱맵_메타태그에서_좌표를_얻는다() {
        // 실제로 밟은 시나리오 — 카카오맵 앱의 '공유'가 주는 place URL엔 좌표가 없고, 페이지가
        // 미리보기로 싣는 스태틱맵 이미지 URL에만 들어있다. 이미 받아온 doc을 쓰므로 추가
        // 네트워크는 0회다. og:image(리뷰 사진)는 지도 호스트가 아니라 그냥 탈락해야 한다.
        Item item = urlItem("https://place.map.kakao.com/15586602");
        aiReturnsEmpty();
        Document placeDoc = Jsoup.parse("""
                <html><head>
                  <meta property="og:image" content="https://img1.kakaocdn.net/review/photo.jpg">
                  <meta name="twitter:image" content="http://staticmap.kakao.com/staticmap/og?\
                type=place&amp;srs=wgs84&amp;size=400x200&amp;service=placeweb\
                &amp;m=126.81519384985194%2C35.18968663709063">
                </head></html>""", "https://place.map.kakao.com/15586602");
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(placeDoc);
        when(openGraphScraper.scrape(placeDoc)).thenReturn(new UrlPreview("수완초밥", null, null));
        when(contentExtractor.extract(placeDoc)).thenReturn(null);
        when(geocoder.reverse(any()))
                .thenReturn(Optional.of("전남광주통합특별시 광산구 장신로50번길 20-3"));

        processor.process(message());

        assertThat(item.getLat()).isEqualTo(35.18968663709063);
        assertThat(item.getLng()).isEqualTo(126.81519384985194);
        assertThat(item.getAddress()).isEqualTo("전남광주통합특별시 광산구 장신로50번길 20-3");
    }

    @Test
    void 역지오코딩이_예외를_던져도_상태와_본문이_유지된다() {
        // 지도 링크 경로에서도 예외가 트랜잭션으로 새면 안 된다(정방향과 같은 위험).
        Item item = urlItem("https://map.kakao.com/link/map/cafe,37.5445,127.0561");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("성수동 카페", null, null));
        when(contentExtractor.extract(doc)).thenReturn("확보한 본문");
        when(geocoder.reverse(any())).thenThrow(new RuntimeException("역지오코딩 폭발"));

        processor.process(message());   // 예외가 밖으로 나오지 않아야 한다

        assertThat(item.getStatus()).isEqualTo(ItemStatus.DONE);
        assertThat(item.getContent()).isEqualTo("확보한 본문");
        assertThat(item.hasCoordinates()).isFalse();   // 좌표까지 함께 유실된다(호출부 catch)
    }
}
