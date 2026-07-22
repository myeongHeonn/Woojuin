package com.ssafy.woojuin.domain.item.processing.url;

import com.ssafy.woojuin.domain.item.Item;
import com.ssafy.woojuin.domain.item.ItemRepository;
import com.ssafy.woojuin.domain.item.ItemType;
import com.ssafy.woojuin.domain.item.processing.ItemProcessingMessage;
import com.ssafy.woojuin.domain.item.processing.ItemProcessor;
import java.net.URI;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * URL 아이템 가공 오케스트레이터 (묶음 D).
 *
 * <p><b>트랙 A(미리보기)</b>: 정규화 → oEmbed(알려진 제공자) → 실패 시 HTML fetch + OG
 * 스크래핑 → 그래도 없으면 도메인명 폴백. 트랙 A는 거의 항상 최소한의 미리보기를 만든다.
 *
 * <p><b>트랙 B(본문 확보)·AI 분석</b>은 후속 단계. 그때 트랙 A가 받아둔 Document를 공유해
 * 본문을 추출하고, DONE으로 승격하는 경로가 추가된다. 지금은 트랙 A만 확보하므로
 * 본문/AI가 아직 없는 반쪽 상태인 PARTIAL로 확정한다.
 */
@Slf4j
@Component
public class UrlItemProcessor implements ItemProcessor {

    private final ItemRepository itemRepository;
    private final UrlNormalizer urlNormalizer;
    private final OEmbedClient oEmbedClient;
    private final HtmlFetcher htmlFetcher;
    private final OpenGraphScraper openGraphScraper;

    public UrlItemProcessor(ItemRepository itemRepository, UrlNormalizer urlNormalizer,
            OEmbedClient oEmbedClient, HtmlFetcher htmlFetcher, OpenGraphScraper openGraphScraper) {
        this.itemRepository = itemRepository;
        this.urlNormalizer = urlNormalizer;
        this.oEmbedClient = oEmbedClient;
        this.htmlFetcher = htmlFetcher;
        this.openGraphScraper = openGraphScraper;
    }

    @Override
    public boolean supports(ItemType type) {
        return type == ItemType.URL;
    }

    /**
     * @Transactional이라 JPA 더티체킹으로 변경이 flush된다. 아이템이 사라졌으면(저장과 처리
     * 사이에 삭제) 조용히 반환한다 — 재시도해도 다시 생기지 않으므로 예외를 던지지 않는다.
     */
    @Override
    @Transactional
    public void process(ItemProcessingMessage message) {
        Item item = itemRepository.findById(message.itemId()).orElse(null);
        if (item == null) {
            log.warn("가공할 아이템이 없음(삭제됨?): itemId={}", message.itemId());
            return;
        }

        String normalizedUrl = urlNormalizer.normalize(item.getUrl());
        UrlPreview preview = resolvePreview(normalizedUrl);
        item.applyPreview(preview.title(), preview.thumbnailUrl(), preview.description());

        // 트랙 B/AI가 아직 없어 본문·분류가 비어 있다. 미리보기만 있는 반쪽 상태.
        item.markPartial();
        log.info("트랙 A 완료(PARTIAL): itemId={}, title={}", item.getId(), preview.title());
    }

    /** oEmbed → OG 스크래핑 → 도메인명 순으로 내려가며 미리보기를 만든다. */
    private UrlPreview resolvePreview(String url) {
        Optional<UrlPreview> oembed = oEmbedClient.fetch(url);
        if (oembed.isPresent() && !oembed.get().hasNothing()) {
            return oembed.get();
        }

        try {
            Document doc = htmlFetcher.fetch(url);
            UrlPreview og = openGraphScraper.scrape(doc);
            if (!og.hasNothing()) {
                return og;
            }
        } catch (HtmlFetchException e) {
            log.info("HTML fetch 실패, 도메인 폴백: url={}, cause={}", url, e.getMessage());
        }

        // 트랙 A 최종 폴백: 최소한 도메인이라도 title로 보여준다.
        return new UrlPreview(domainOf(url), null, null);
    }

    private String domainOf(String url) {
        try {
            String host = new URI(url).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }
}
