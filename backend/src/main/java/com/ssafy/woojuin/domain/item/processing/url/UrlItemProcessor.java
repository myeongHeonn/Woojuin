package com.ssafy.woojuin.domain.item.processing.url;

import com.ssafy.woojuin.domain.item.Item;
import com.ssafy.woojuin.domain.item.ItemRepository;
import com.ssafy.woojuin.domain.item.ItemType;
import com.ssafy.woojuin.domain.item.processing.ItemProcessingMessage;
import com.ssafy.woojuin.domain.item.processing.ItemProcessor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * URL 아이템 가공 (묶음 D).
 *
 * <p><b>지금은 얇은 관통 단계</b> — Jsoup으로 og:title(없으면 &lt;title&gt;) 하나만 긁어
 * 저장하고 DONE으로 끝낸다. 정규화·oEmbed·본문 추출(트랙 B)·AI 분석은 이 골격이 실제
 * URL로 도는 걸 확인한 뒤 각 조각을 교체/추가하는 방식으로 채운다.
 */
@Slf4j
@Component
public class UrlItemProcessor implements ItemProcessor {

    /** 서버가 사용자 URL을 직접 받아오므로 상식적인 상한을 둔다. 세부 정책은 트랙 A 확장 때 강화. */
    private static final int FETCH_TIMEOUT_MS = 5000;

    private final ItemRepository itemRepository;

    public UrlItemProcessor(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public boolean supports(ItemType type) {
        return type == ItemType.URL;
    }

    /**
     * 아이템을 로드해 미리보기를 채우고 상태를 확정한다. @Transactional이라 JPA 더티체킹으로
     * 변경이 flush된다. 아이템이 사라졌으면(저장과 처리 사이에 삭제) 조용히 반환한다 —
     * 재시도해도 다시 생기지 않으므로 컨슈머가 ACK하도록 예외를 던지지 않는다.
     */
    @Override
    @Transactional
    public void process(ItemProcessingMessage message) {
        Item item = itemRepository.findById(message.itemId()).orElse(null);
        if (item == null) {
            log.warn("가공할 아이템이 없음(삭제됨?): itemId={}", message.itemId());
            return;
        }

        UrlPreview preview = scrapePreview(item.getUrl());
        if (preview.hasNothing()) {
            // 트랙 A조차 실패 — 사용자에게 URL만 남는다. 트랙 B/폴백은 후속 단계에서.
            item.markFailed();
            log.info("트랙 A 실패: itemId={}, url={}", item.getId(), item.getUrl());
            return;
        }

        item.applyPreview(preview.title(), preview.thumbnailUrl(), preview.description());
        item.markDone();
        log.info("트랙 A 성공: itemId={}, title={}", item.getId(), preview.title());
    }

    private UrlPreview scrapePreview(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(FETCH_TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
            String title = firstNonBlank(
                    doc.selectFirst("meta[property=og:title]") != null
                            ? doc.selectFirst("meta[property=og:title]").attr("content") : null,
                    doc.title());
            return new UrlPreview(title, null, null);
        } catch (Exception e) {
            // 네트워크/파싱 실패는 여기서 흡수한다. 트랙 A 실패가 컨슈머 재시도를 유발하면
            // 안 되기 때문 — 재시도 정책은 후속 단계에서 별도로 설계한다.
            log.warn("미리보기 스크래핑 실패: url={}, cause={}", url, e.toString());
            return UrlPreview.empty();
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
