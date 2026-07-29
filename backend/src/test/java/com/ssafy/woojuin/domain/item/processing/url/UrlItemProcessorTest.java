package com.ssafy.woojuin.domain.item.processing.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.ai.AiAnalysis;
import com.ssafy.woojuin.domain.ai.AiAnalyzer;
import com.ssafy.woojuin.domain.category.service.CategoryAssignmentService;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.processing.ItemProcessingMessage;
import com.ssafy.woojuin.domain.location.GeoPoint;
import com.ssafy.woojuin.domain.location.Geocoder;
import com.ssafy.woojuin.domain.location.KoreanAddressExtractor;
import com.ssafy.woojuin.domain.location.LocationResolver;
import com.ssafy.woojuin.domain.location.MapLinkCoordinateParser;
import com.ssafy.woojuin.domain.location.ResolvedLocation;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.util.List;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        // 파서·추출기는 순수 함수라 실제 구현을 쓰고 외부 호출이 필요한 지오코더만 목으로 둔다.
        LocationResolver locationResolver = new LocationResolver(
                new MapLinkCoordinateParser(), new KoreanAddressExtractor(), geocoder);
        processor = new UrlItemProcessor(itemRepository, normalizer, oEmbedClient,
                htmlFetcher, openGraphScraper, contentExtractor, aiAnalyzer,
                categoryAssignmentService, locationResolver);
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
    void fetch_실패하면_도메인폴백_PARTIAL() {
        Item item = urlItem();
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenThrow(new HtmlFetchException("차단됨"));

        processor.process(message());

        assertThat(item.getStatus()).isEqualTo(ItemStatus.PARTIAL);
        assertThat(item.getTitle()).isEqualTo("example.com");   // 호스트 폴백
    }

    @Test
    void AI가_요약과_카테고리를_주면_저장한다() {
        Item item = urlItem();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("제목", null, null));
        when(contentExtractor.extract(doc)).thenReturn("본문");
        when(aiAnalyzer.analyze(any())).thenReturn(new AiAnalysis("요약문", List.of("학습·지식")));

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

    // ---------- 위치 확보 (FR-023) ----------

    @Test
    void 지도_공유링크면_지오코더_없이_좌표를_저장한다() {
        Item item = urlItem("https://map.kakao.com/link/map/cafe,37.5445,127.0561");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("성수동 카페", null, null));
        when(contentExtractor.extract(doc)).thenReturn("본문");

        processor.process(message());

        assertThat(item.getLat()).isEqualTo(37.5445);
        assertThat(item.getLng()).isEqualTo(127.0561);
        assertThat(item.getAddress()).isNull();   // 링크는 좌표만 준다
        verifyNoInteractions(geocoder);           // 외부 호출 0회
    }

    @Test
    void 지도_링크가_아니면_본문_주소를_지오코딩해_저장한다() {
        Item item = urlItem("https://blog.naver.com/someone/123");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc))
                .thenReturn(new UrlPreview("성수동 맛집", null, "서울 성동구 아차산로 49"));
        when(contentExtractor.extract(doc)).thenReturn("본문");
        when(geocoder.forwardAddress("서울 성동구 아차산로 49")).thenReturn(Optional.of(
                new ResolvedLocation(new GeoPoint(37.5445, 127.0561), "서울 성동구 아차산로17길 49")));

        processor.process(message());

        assertThat(item.getLat()).isEqualTo(37.5445);
        assertThat(item.getAddress()).isEqualTo("서울 성동구 아차산로17길 49");
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
        Item item = urlItem("https://blog.naver.com/someone/123");
        aiReturnsEmpty();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc))
                .thenReturn(new UrlPreview("성수동 맛집", "https://img", "서울 성동구 아차산로 49"));
        when(contentExtractor.extract(doc)).thenReturn("확보한 본문");
        when(geocoder.forwardAddress(any())).thenThrow(new RuntimeException("지오코딩 폭발"));

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

        processor.process(message());

        assertThat(item.getLat()).isEqualTo(37.5445);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.PARTIAL);
    }
}
