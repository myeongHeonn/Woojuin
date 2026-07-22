package com.ssafy.woojuin.domain.item.processing.url;

import com.ssafy.woojuin.domain.ai.AiAnalysis;
import com.ssafy.woojuin.domain.ai.AiAnalysisRequest;
import com.ssafy.woojuin.domain.ai.AiAnalyzer;
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
 * 스크래핑 → 그래도 없으면 도메인명 폴백. 거의 항상 최소 미리보기를 만든다.
 *
 * <p><b>트랙 B(본문 확보)</b>: 트랙 A가 받아둔 Document를 재활용해 readability4j로 본문을
 * 뽑는다. 두 트랙은 완전히 격리돼 한쪽 실패가 다른 쪽에 영향을 주지 않는다.
 *
 * <p><b>상태 전이</b>는 AGENTS.md 정의를 따른다 — 트랙 B(콘텐츠) 성공 여부로만 정한다.
 * <ul>
 *   <li>트랙 B 성공 → DONE</li>
 *   <li>트랙 A만 성공(트랙 B 실패) → PARTIAL</li>
 *   <li>트랙 A조차 실패(도메인 폴백도 불가) → FAILED (사실상 URL 파싱 불가일 때만)</li>
 * </ul>
 *
 * <p><b>AI 분석</b>은 상태와 무관한 별개 보강 단계다. 본문이 있으면 {@link AiAnalyzer}에
 * 넘기지만, 요약·카테고리·태그를 저장할 스키마(ai_results/categories/tags)가 아직 없어
 * 결과를 영속화하지 못한다. 스키마가 생기면 이 지점에서 매핑을 붙인다.
 */
@Slf4j
@Component
public class UrlItemProcessor implements ItemProcessor {

    private final ItemRepository itemRepository;
    private final UrlNormalizer urlNormalizer;
    private final OEmbedClient oEmbedClient;
    private final HtmlFetcher htmlFetcher;
    private final OpenGraphScraper openGraphScraper;
    private final ContentExtractor contentExtractor;
    private final AiAnalyzer aiAnalyzer;

    public UrlItemProcessor(ItemRepository itemRepository, UrlNormalizer urlNormalizer,
            OEmbedClient oEmbedClient, HtmlFetcher htmlFetcher, OpenGraphScraper openGraphScraper,
            ContentExtractor contentExtractor, AiAnalyzer aiAnalyzer) {
        this.itemRepository = itemRepository;
        this.urlNormalizer = urlNormalizer;
        this.oEmbedClient = oEmbedClient;
        this.htmlFetcher = htmlFetcher;
        this.openGraphScraper = openGraphScraper;
        this.contentExtractor = contentExtractor;
        this.aiAnalyzer = aiAnalyzer;
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

        // 트랙 A: 미리보기. OG 스크래핑에 쓴 Document는 트랙 B가 재활용하도록 넘겨받는다.
        Document doc = null;
        UrlPreview preview;
        Optional<UrlPreview> oembed = oEmbedClient.fetch(normalizedUrl);
        if (oembed.isPresent() && !oembed.get().hasNothing()) {
            preview = oembed.get();   // 미디어 제공자는 본문이 없어 트랙 B 대상이 아니다(doc=null)
        } else {
            doc = tryFetch(normalizedUrl);
            preview = (doc != null) ? openGraphScraper.scrape(doc) : UrlPreview.empty();
        }
        if (preview.hasNothing()) {
            preview = new UrlPreview(domainOf(normalizedUrl), null, null);
        }
        item.applyPreview(preview.title(), preview.thumbnailUrl(), preview.description());

        // 트랙 B: 본문 확보. Document가 없으면(oEmbed 경로/트랙 A fetch 실패) 본문도 없다.
        String content = (doc != null) ? contentExtractor.extract(doc) : null;
        boolean contentAcquired = content != null;
        if (contentAcquired) {
            item.applyContent(content);
            analyzeQuietly(item, content);   // 보강 단계, 상태에 영향 없음
        }

        finalizeStatus(item, preview, contentAcquired);
    }

    private Document tryFetch(String url) {
        try {
            return htmlFetcher.fetch(url);
        } catch (HtmlFetchException e) {
            log.info("HTML fetch 실패: url={}, cause={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * AI 보강. 결과를 저장할 스키마가 아직 없어 지금은 호출 seam만 살아 있다 — 묶음 F가
     * 실제 AiAnalyzer를, 카테고리/AI결과 도메인이 저장 컬럼을 붙이면 여기서 매핑한다.
     * 어떤 실패도 이미 확보한 본문/미리보기를 무효화하면 안 되므로 조용히 흡수한다.
     */
    private void analyzeQuietly(Item item, String content) {
        try {
            AiAnalysis analysis = aiAnalyzer.analyze(new AiAnalysisRequest(item.getTitle(), content));
            if (!analysis.isEmpty()) {
                log.debug("AI 분석 결과 수신(미영속): itemId={}, category={}", item.getId(), analysis.category());
                // TODO: categories/ai_results/tags 스키마 생기면 여기서 item에 반영
            }
        } catch (Exception e) {
            log.warn("AI 분석 실패(무시): itemId={}, cause={}", item.getId(), e.toString());
        }
    }

    private void finalizeStatus(Item item, UrlPreview preview, boolean contentAcquired) {
        if (contentAcquired) {
            item.markDone();
        } else if (!preview.hasNothing()) {
            item.markPartial();
        } else {
            item.markFailed();   // 도메인 폴백조차 비었을 때 — 사실상 URL 파싱 불가
        }
        log.info("URL 가공 완료: itemId={}, status={}", item.getId(), item.getStatus());
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
