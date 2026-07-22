package com.ssafy.woojuin.domain.item.processing.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.ai.AiAnalysis;
import com.ssafy.woojuin.domain.ai.AiAnalyzer;
import com.ssafy.woojuin.domain.item.Item;
import com.ssafy.woojuin.domain.item.ItemRepository;
import com.ssafy.woojuin.domain.item.ItemType;
import com.ssafy.woojuin.domain.item.processing.ItemProcessingMessage;
import com.ssafy.woojuin.global.common.ItemStatus;
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
 * PARTIAL/FAILED가 나오는지에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
class UrlItemProcessorTest {

    @Mock ItemRepository itemRepository;
    @Mock OEmbedClient oEmbedClient;
    @Mock HtmlFetcher htmlFetcher;
    @Mock OpenGraphScraper openGraphScraper;
    @Mock ContentExtractor contentExtractor;
    @Mock AiAnalyzer aiAnalyzer;

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
        processor = new UrlItemProcessor(itemRepository, normalizer, oEmbedClient,
                htmlFetcher, openGraphScraper, contentExtractor, aiAnalyzer);
    }

    private Item urlItem() {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.URL)
                .url("https://example.com/a").build();
        when(itemRepository.findById(any())).thenReturn(Optional.of(item));
        return item;
    }

    private ItemProcessingMessage message() {
        return new ItemProcessingMessage(1L, 1L, ItemType.URL);
    }

    @Test
    void 트랙B_성공하면_본문저장하고_DONE() {
        Item item = urlItem();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("제목", "https://img", "설명"));
        when(contentExtractor.extract(doc)).thenReturn("충분히 긴 본문 텍스트");
        when(aiAnalyzer.analyze(any())).thenReturn(AiAnalysis.empty());

        processor.process(message());

        assertThat(item.getStatus()).isEqualTo(ItemStatus.DONE);
        assertThat(item.getTitle()).isEqualTo("제목");
        assertThat(item.getContent()).isEqualTo("충분히 긴 본문 텍스트");
    }

    @Test
    void 트랙A만_성공하면_PARTIAL() {
        Item item = urlItem();
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenReturn(doc);
        when(openGraphScraper.scrape(doc)).thenReturn(new UrlPreview("제목", null, null));
        when(contentExtractor.extract(doc)).thenReturn(null);   // 트랙 B 실패

        processor.process(message());

        assertThat(item.getStatus()).isEqualTo(ItemStatus.PARTIAL);
        assertThat(item.getTitle()).isEqualTo("제목");
        assertThat(item.getContent()).isNull();
        verifyNoInteractions(aiAnalyzer);   // 본문 없으면 AI도 호출 안 함
    }

    @Test
    void oEmbed_성공하면_본문없이_PARTIAL() {
        Item item = urlItem();
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
        when(oEmbedClient.fetch(any())).thenReturn(Optional.empty());
        when(htmlFetcher.fetch(any())).thenThrow(new HtmlFetchException("차단됨"));

        processor.process(message());

        assertThat(item.getStatus()).isEqualTo(ItemStatus.PARTIAL);
        assertThat(item.getTitle()).isEqualTo("example.com");   // 호스트 폴백
    }

    @Test
    void 아이템이_사라졌으면_조용히_반환한다() {
        when(itemRepository.findById(any())).thenReturn(Optional.empty());

        processor.process(message());   // 예외 없이 통과

        verifyNoInteractions(oEmbedClient, htmlFetcher, contentExtractor, aiAnalyzer);
    }
}
