package com.ssafy.woojuin.domain.item.processing.url;

import com.ssafy.woojuin.domain.ai.AiAnalysis;
import com.ssafy.woojuin.domain.ai.AiAnalysisRequest;
import com.ssafy.woojuin.domain.ai.AiAnalyzer;
import com.ssafy.woojuin.domain.ai.AiSourceType;
import com.ssafy.woojuin.domain.ai.CategoryCandidate;
import com.ssafy.woojuin.domain.category.service.CategoryAssignmentService;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.event.ItemDoneEvent;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.processing.ItemProcessingMessage;
import com.ssafy.woojuin.domain.item.processing.ItemProcessor;
import com.ssafy.woojuin.domain.location.LocationResolver;
import com.ssafy.woojuin.domain.location.ResolvedLocation;
import com.ssafy.woojuin.global.common.ItemStatus;
import com.ssafy.woojuin.global.common.TransactionRunner;
import com.ssafy.woojuin.global.sse.WorkspaceChangedEvent;
import com.ssafy.woojuin.global.sse.WorkspaceEventType;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * URL 아이템 가공 오케스트레이터 (묶음 D).
 *
 * <p><b>트랙 A(미리보기)</b>: 정규화 → oEmbed(알려진 제공자) → 실패 시 HTML fetch + OG
 * 스크래핑 → 그래도 없으면 도메인명 폴백. 거의 항상 최소 미리보기를 만든다.
 *
 * <p><b>트랙 B(본문 확보)</b>: 트랙 A가 받아둔 Document를 재활용해 readability4j로 본문을
 * 뽑는다. 두 트랙은 완전히 격리돼 한쪽 실패가 다른 쪽에 영향을 주지 않는다.
 *
 * <p><b>AI 보강</b>: 본문이 없어도(PARTIAL) title만으로 분류하도록 항상 시도한다. 요약은
 * 본문이 있을 때만 채워지고, 카테고리는 그 워크스페이스의 현재 카테고리 중에서 배정된다.
 * 보강 실패는 이미 확보한 미리보기·본문을 무효화하지 않는다.
 *
 * <p><b>상태 전이</b>는 AGENTS.md 정의를 따른다 — 트랙 B(콘텐츠) 성공 여부로만 정한다.
 * <ul>
 *   <li>트랙 B 성공 → DONE</li>
 *   <li>트랙 A만 성공(트랙 B 실패) → PARTIAL</li>
 *   <li>트랙 A조차 실패(도메인 폴백도 불가) → FAILED (사실상 URL 파싱 불가일 때만)</li>
 * </ul>
 */
@Slf4j
@Component
public class UrlItemProcessor implements ItemProcessor {

    /** 본문에서 모을 지도 이미지 후보 상한. 지도는 보통 글에 하나뿐이다. */
    private static final int MAX_EMBEDDED_MAP_CANDIDATES = 5;

    /** {@code items.title} 컬럼 길이. 폴백 제목이 이걸 넘기면 안 된다. */
    private static final int MAX_TITLE_LENGTH = 500;

    private final ItemRepository itemRepository;
    private final UrlNormalizer urlNormalizer;
    private final OEmbedClient oEmbedClient;
    private final HtmlFetcher htmlFetcher;
    private final OpenGraphScraper openGraphScraper;
    private final ContentExtractor contentExtractor;
    private final AiAnalyzer aiAnalyzer;
    private final CategoryAssignmentService categoryAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final LocationResolver locationResolver;
    private final TransactionRunner tx;

    public UrlItemProcessor(ItemRepository itemRepository, UrlNormalizer urlNormalizer,
            OEmbedClient oEmbedClient, HtmlFetcher htmlFetcher, OpenGraphScraper openGraphScraper,
            ContentExtractor contentExtractor, AiAnalyzer aiAnalyzer,
            CategoryAssignmentService categoryAssignmentService, ApplicationEventPublisher eventPublisher,
            LocationResolver locationResolver, TransactionRunner tx) {
        this.itemRepository = itemRepository;
        this.urlNormalizer = urlNormalizer;
        this.oEmbedClient = oEmbedClient;
        this.htmlFetcher = htmlFetcher;
        this.openGraphScraper = openGraphScraper;
        this.contentExtractor = contentExtractor;
        this.aiAnalyzer = aiAnalyzer;
        this.categoryAssignmentService = categoryAssignmentService;
        this.eventPublisher = eventPublisher;
        this.locationResolver = locationResolver;
        this.tx = tx;
    }

    @Override
    public boolean supports(ItemType type) {
        return type == ItemType.URL;
    }

    /** 2단계(외부 호출)가 모아 온 결과. 3단계(쓰기 트랜잭션)가 한 번에 반영한다. */
    private record Gathered(UrlPreview preview, String content, ResolvedLocation location, AiAnalysis analysis) {
    }

    /**
     * <b>트랜잭션 경계</b>: 이 메서드에 @Transactional을 걸지 않는다 — 크롤링·LLM 호출
     * (수십 초) 내내 커넥션을 점유해 풀이 마르기 때문이다({@link TransactionRunner} javadoc의
     * 사고 기록). 외부 호출은 전부 트랜잭션 밖에서 하고, 마지막에 {@code tx.write()} 안에서
     * 다시 로드해 미리보기·본문·요약·카테고리를 한 트랜잭션으로 함께 커밋한다.
     *
     * <p>아이템이 사라졌으면(저장과 처리 사이 삭제) 조용히 반환한다 — 재시도해도 다시
     * 생기지 않으므로 예외를 던지지 않는다. 외부 호출 동안 삭제·처리됐을 수도 있으므로
     * 반영 트랜잭션 안에서 가드를 재확인한다.
     */
    @Override
    public void process(ItemProcessingMessage message) {
        // 1단계(짧은 읽기): 스냅숏 확보 + 가드. 이 엔티티는 트랜잭션 밖에서 읽기 전용으로만 쓴다.
        Item snapshot = loadProcessable(message.itemId());
        if (snapshot == null) {
            return;
        }

        // 2단계(트랜잭션 없음): 크롤링·좌표·LLM. 개별 실패는 기존과 동일하게 각자 흡수한다.
        Gathered gathered = gather(snapshot);

        // 3단계(짧은 쓰기): 다시 로드해 가드를 재확인하고 전부 반영·확정한다.
        tx.write(() -> {
            Item item = itemRepository.findById(message.itemId()).orElse(null);
            if (item == null) {
                log.warn("반영할 아이템이 없음(외부 호출 중 삭제됨?): itemId={}", message.itemId());
                return;
            }
            if (item.getStatus() != ItemStatus.PROCESSING) {
                log.info("이미 처리된 아이템, 반영 스킵: itemId={}, status={}", item.getId(), item.getStatus());
                return;
            }
            item.applyPreview(gathered.preview().title(), gathered.preview().thumbnailUrl(),
                    gathered.preview().description());
            item.applyContent(gathered.content());   // null이면 무시(엔티티 계약)
            if (gathered.location() != null) {
                item.applyLocation(gathered.location().lat(), gathered.location().lng(),
                        gathered.location().address());
            }
            if (gathered.analysis() != null) {
                item.update(gathered.analysis().title(), null);   // AI가 다듬은 제목(null이면 기존 유지)
                item.applySummary(gathered.analysis().summary());
                categoryAssignmentService.assign(item.getId(), item.getWorkspaceId(),
                        gathered.analysis().categories());
            }
            finalizeStatus(item, gathered.preview(), gathered.content() != null);
        });
    }

    /** 가공 대상 아이템을 읽는다. 없거나 이미 처리됐으면 null — 외부 호출 전에 거른다. */
    private Item loadProcessable(Long itemId) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            log.warn("가공할 아이템이 없음(삭제됨?): itemId={}", itemId);
            return null;
        }
        if (item.getStatus() != ItemStatus.PROCESSING) {
            // DB 커밋은 됐는데 큐 ACK 직전에 죽는 등 at-least-once 큐 특성상 이미 끝난
            // 메시지가 재배달될 수 있다. AI를 또 호출하지 않도록 여기서 막는다.
            log.info("이미 처리된 아이템, 재처리 스킵: itemId={}, status={}", item.getId(), item.getStatus());
            return null;
        }
        return item;
    }

    /** 2단계: 네트워크가 필요한 작업 전부. DB 커넥션을 잡지 않은 채 수십 초가 걸려도 된다. */
    private Gathered gather(Item snapshot) {
        String normalizedUrl = urlNormalizer.normalize(snapshot.getUrl());

        // 트랙 A: 미리보기. OG 스크래핑에 쓴 Document는 트랙 B가 재활용하도록 넘겨받는다.
        Document doc = null;
        UrlPreview preview;
        Optional<UrlPreview> oembed = oEmbedClient.fetch(normalizedUrl);
        if (oembed.isPresent() && !oembed.get().hasNothing()) {
            preview = oembed.get();   // 미디어 제공자는 본문이 없어 트랙 B 대상이 아니다(doc=null)
        } else {
            doc = refetchIfRedirectRevealedBetterUrl(tryFetch(normalizedUrl));
            preview = (doc != null) ? openGraphScraper.scrape(doc) : UrlPreview.empty();
        }
        if (preview.hasNothing()) {
            preview = new UrlPreview(fallbackTitleOf(snapshot.getUrl()), null, null);
        }

        // 트랙 B: 본문 확보. Document가 없으면(oEmbed 경로/트랙 A fetch 실패) 본문도 없다.
        String content = (doc != null) ? contentExtractor.extract(doc) : null;

        // 위치 확보(FR-023). 썸네일과 같은 등급의 부가 정보라 상태에는 영향이 없다.
        ResolvedLocation location = tryResolveLocation(snapshot, normalizedUrl, doc);

        // AI 분석은 applyPreview가 채웠을 제목을 봐야 한다 — 반영 전이므로 같은 규칙으로 고른다.
        String effectiveTitle = hasText(snapshot.getTitle()) ? snapshot.getTitle() : preview.title();
        AiAnalysis analysis = analyzeSafely(snapshot, effectiveTitle, content);

        return new Gathered(preview, content, location, analysis);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 지도 좌표를 확보해 반영한다 (FR-023). <b>좌표는 지도에서만 온다</b> — 지도 공유 링크,
     * 페이지가 실어준 지도 이미지, 본문에 임베드된 지도. 전부 URL 문자열 파싱이라 네트워크가
     * 0회이고, 좌표를 얻었을 때만 주소를 채우려고 역지오코딩 1회가 나간다.
     *
     * <p>본문 텍스트에서 주소를 뽑는 경로는 <b>의도적으로 없다</b> — 이유는
     * {@link LocationResolver} javadoc에 있다.
     *
     * <p>후보 URL에 <b>원본 {@code item.getUrl()}까지</b> 넣는 이유: {@link UrlNormalizer}가
     * fragment를 버리고 일부 파라미터를 지우는데, 구형 구글맵은 좌표를 {@code #} 뒤에 담았다.
     * {@code doc.location()}은 리다이렉트가 끝난 최종 URL이라 단축 링크(naver.me 등)를
     * <b>추가 요청 없이</b> 커버한다 — 그 fetch는 {@link JsoupHtmlFetcher}가 홉마다 SSRF를
     * 검사한 경로다.
     *
     * <p>URL 뒤에 <b>페이지의 og:image·twitter:image</b>도 후보로 붙인다. 카카오 장소 페이지
     * ({@code place.map.kakao.com/{id}})는 URL에 좌표가 없지만 미리보기 스태틱맵 이미지 URL에
     * 정확한 좌표를 담고 있어서, 이미 받아온 {@code doc}만으로 좌표가 나온다(추가 네트워크 0회).
     * 카카오맵 앱의 '공유'가 주는 형태라 실사용 빈도가 가장 높다. 지도 이미지가 아닌 og:image는
     * {@link MapLinkCoordinateParser}의 호스트 게이트에서 그냥 탈락하므로 넣어도 무해하다 —
     * 단 하나 위험한 구글 스태틱맵은 그쪽에서 명시적으로 거부한다(요청자 IP 기준 좌표라서).
     *
     * <p>마지막으로 <b>본문에 임베드된 지도</b>를 붙인다. 맛집 블로그는 글 안에 지도를 심는데
     * 그 정적 지도 이미지 URL에 <b>글쓴이가 직접 찍은 핀</b> 좌표가 들어있다. 이게 이 앱에서
     * 가장 흔한 "장소가 있는 글"이고, 지식·기술 글은 지도를 심지 않으므로 이 신호만으로
     * 두 부류가 갈린다.
     *
     * <p>모든 실패를 흡수하고 null을 돌려준다 — 위치가 없다고 이미 확보한 미리보기·본문을
     * 버리면 안 된다(예외가 새어나가면 디스패처가 RETRYABLE로 판단해 AI 호출까지 포함한
     * 파이프라인 전체가 재실행된다). {@code Geocoder} 계약도 "예외를 던지지 않는다"지만
     * 이중으로 막는다.
     */
    private ResolvedLocation tryResolveLocation(Item snapshot, String normalizedUrl, Document doc) {
        try {
            List<String> candidateUrls = Stream.concat(
                            Stream.of(snapshot.getUrl(), normalizedUrl,
                                    doc != null ? doc.location() : null,
                                    metaContent(doc, "meta[name='twitter:image']"),
                                    metaContent(doc, "meta[property='og:image']")),
                            embeddedMapUrls(doc).stream())
                    .filter(url -> url != null && !url.isBlank())
                    .toList();

            return locationResolver.resolveForUrlItem(candidateUrls).orElse(null);
        } catch (Exception e) {
            log.warn("위치 확보 실패(무시): itemId={}", snapshot.getId(), e);
            return null;
        }
    }

    /** 메타 태그 하나의 content 값. 없으면 null — 지도 이미지가 아닌 값은 파서가 걸러낸다. */
    private String metaContent(Document doc, String selector) {
        if (doc == null) {
            return null;
        }
        Element meta = doc.selectFirst(selector);
        return meta == null ? null : meta.attr("content");
    }

    /**
     * 본문에 임베드된 정적 지도 이미지 URL을 모은다. 선택자는 <b>값싼 사전 필터</b>일 뿐이고
     * 호스트 판별은 {@link MapLinkCoordinateParser}가 한다 — 그래서 여기서 조금 넉넉하게 잡아도
     * 엉뚱한 이미지가 좌표가 되지는 않는다.
     *
     * <p>사진이 많은 글에서 후보가 폭발하지 않게 상한을 둔다. 지도는 보통 글에 하나뿐이라
     * 상한에 걸려 놓칠 일은 사실상 없다.
     */
    private List<String> embeddedMapUrls(Document doc) {
        if (doc == null) {
            return List.of();
        }
        return Stream.concat(
                        doc.select("img[src*=static.map], img[src*=staticmap]").stream()
                                .map(img -> attrOrAbs(img, "src")),
                        // 네이버 장소 페이지의 '길찾기' 링크가 좌표를 담는다(nso_path).
                        doc.select("a[href*=nso_path]").stream()
                                .map(a -> attrOrAbs(a, "href")))
                .filter(url -> !url.isBlank())
                .limit(MAX_EMBEDDED_MAP_CANDIDATES)
                .toList();
    }

    /** 상대 경로도 절대화해서 호스트 판별이 되게 한다. 절대화가 안 되면 원본을 쓴다. */
    private String attrOrAbs(Element element, String attribute) {
        String absolute = element.attr("abs:" + attribute);
        return absolute.isBlank() ? element.attr(attribute) : absolute;
    }

    /**
     * 단축 링크는 <b>리다이렉트가 끝나야</b> 정규화 대상이 드러난다. {@code naver.me/xxxx}는
     * 정규화 시점엔 불투명한 토큰이고, 해소된 뒤에야 네이버 지도 장소나 블로그 글임을 알 수 있다.
     * 그 최종 URL을 다시 정규화해 달라지면 <b>한 번만</b> 다시 받아온다.
     *
     * <p>이게 없으면 {@code naver.me} 지도 링크는 2.3KB SPA 껍데기에서 끝나고(미리보기·좌표 전무),
     * {@code naver.me} 블로그 링크도 본문 없는 데스크톱 껍데기를 받는다.
     *
     * <p>추가 요청은 최대 1회이고, 정규화가 URL을 바꾸는 단축 링크에서만 발생한다. 재요청이
     * 실패하면 <b>원래 문서를 그대로 쓴다</b> — 이 최적화 때문에 아이템 가공이 실패하면 안 된다.
     */
    private Document refetchIfRedirectRevealedBetterUrl(Document doc) {
        if (doc == null) {
            return null;
        }
        String resolved = doc.location();
        String renormalized = urlNormalizer.betterUrlOnAnotherHost(resolved).orElse(null);
        if (renormalized == null) {
            return doc;
        }
        log.info("리다이렉트 해소 후 정규화가 달라져 다시 받아온다: {} → {}", resolved, renormalized);
        Document better = tryFetch(renormalized);
        return better != null ? better : doc;
    }

    /**
     * fetch 실패를 흡수하고 null을 돌려준다 — 호출부는 미리보기 없이 진행한다.
     *
     * <p><b>일시적 실패(429·503)라도 재시도하지 않는다. 의도된 선택이다.</b> 예외를 밖으로
     * 던지면 디스패처가 RETRYABLE로 보고 스트림이 최대 3번 재배달하는데, 그 대가가 이득보다
     * 크다 — 파이프라인 전체(AI 호출 포함)가 다시 돌고, 계속 막히는 상대라면 결말이 PARTIAL이
     * 아니라 <b>FAILED</b>가 되어 사용자 입장에선 더 나빠진다.
     *
     * <p>네이버 스토어가 이 케이스다. IP 단위 레이트리밋이라 같은 링크가 시점에 따라 되다
     * 안 되다 하고, 호스트·UA를 바꿔도 스텔스 크롤러까지 함께 막힌다. 대신 아이템은 남고
     * 폴백 제목이 호스트+경로를 담으므로({@link #fallbackTitleOf}) 사용자가 무엇인지 알아보고
     * 상세에서 원본 링크로 갈 수 있다. 다시 저장하면 그때는 대개 성공한다.
     *
     * <p>재처리가 정말 필요해지면 재시도가 아니라 별도 경로여야 한다 — 프로세서는
     * {@code status != PROCESSING}이면 조기 반환하고, 일괄 재처리는 사용자의 수동 편집을
     * 덮어쓸 위험이 있다({@code ItemService.update} javadoc 참고).
     */
    private Document tryFetch(String url) {
        try {
            return htmlFetcher.fetch(url);
        } catch (HtmlFetchException e) {
            log.info("HTML fetch 실패: url={}, cause={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * AI 요약·분류를 수행한다. 후보 카테고리(그 워크스페이스의 현재 목록)를 넘긴다 —
     * 본문이 없어도 title로 분류를 시도한다(상태에는 영향 없음). 어떤 실패도 이미 확보한
     * 본문·미리보기를 무효화하면 안 되므로 조용히 흡수하고 null을 돌려준다.
     */
    private AiAnalysis analyzeSafely(Item snapshot, String effectiveTitle, String content) {
        try {
            List<CategoryCandidate> candidates = categoryAssignmentService.candidates(snapshot.getWorkspaceId());
            return aiAnalyzer.analyze(
                    new AiAnalysisRequest(AiSourceType.URL, effectiveTitle, content, candidates));
        } catch (Exception e) {
            // 스택트레이스 포함 — 래퍼 예외에 묻힌 근본 원인(DB 커넥션 등)이 보여야 한다.
            log.warn("AI 보강 실패(무시): itemId={}", snapshot.getId(), e);
            return null;
        }
    }

    private void finalizeStatus(Item item, UrlPreview preview, boolean contentAcquired) {
        if (contentAcquired) {
            item.markDone();
            eventPublisher.publishEvent(new ItemDoneEvent(item.getId()));
        } else if (!preview.hasNothing()) {
            item.markPartial();
            // PARTIAL도 미리보기(제목·썸네일)는 있으니 알려준다 — 도메인 폴백조차 없는 FAILED만 생략.
            eventPublisher.publishEvent(new ItemDoneEvent(item.getId()));
        } else {
            item.markFailed();   // 도메인 폴백조차 비었을 때 — 사실상 URL 파싱 불가. 알림 생략(AGENTS.md 규칙 6).
        }
        eventPublisher.publishEvent(WorkspaceChangedEvent.of(item.getWorkspaceId(), WorkspaceEventType.ITEM));
        log.info("URL 가공 완료: itemId={}, status={}", item.getId(), item.getStatus());
    }

    /**
     * 미리보기를 하나도 못 얻었을 때 쓸 제목. <b>호스트만 쓰지 않고 경로까지 붙인다</b> —
     * 같은 쇼핑몰 링크를 여러 개 저장했을 때 카드가 전부 "smartstore.naver.com"으로 보이면
     * 어느 게 어느 상품인지 구별할 수 없다. 경로가 있으면 최소한 사용자가 알아볼 단서가 남고,
     * 상세 화면의 원본 URL과도 이어진다.
     *
     * <p>스킴과 쿼리는 버린다 — {@code https://}는 정보가 없고 쿼리는 추적 파라미터(네이버
     * {@code NaPm=...} 등)로 수백 자가 되기도 해서 제목으로는 방해만 된다.
     *
     * <p>미리보기 실패는 대개 일시적이다(봇 차단·레이트리밋). 그래도 아이템은 남아야 하고,
     * 사용자가 원본 링크로 갈 수 있으면 최소한의 값은 한다.
     */
    private String fallbackTitleOf(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) {
                return truncateTitle(url);
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return truncateTitle(host + path);
        } catch (Exception e) {
            return truncateTitle(url);
        }
    }

    /** title 컬럼이 500자라 넘치지 않게 자른다. */
    private String truncateTitle(String value) {
        return value.length() <= MAX_TITLE_LENGTH ? value : value.substring(0, MAX_TITLE_LENGTH);
    }
}
