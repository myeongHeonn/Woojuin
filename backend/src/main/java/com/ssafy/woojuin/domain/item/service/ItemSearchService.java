package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSearchResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSummaryResponse;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.repository.ItemSearchRepository;
import com.ssafy.woojuin.domain.item.repository.MatchMode;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 통합 검색(FR-030) — 제목·AI 요약·본문(메모/추출 본문/OCR 텍스트)·미리보기 설명을 한 번에
 * 훑는다. 휴지통 아이템은 제외한다.
 *
 * <p><b>검색 단계 설계</b>:
 * <ol>
 *   <li>모든 단어를 포함하는 결과(ALL) — 정확도 우선. 문구가 그대로 있는 아이템
 *       ("여권 사진 규격")이 있으면 그게 최선이므로 여기서는 아무것도 안 뺀다</li>
 *   <li>0건이면 형식 단어({@link #FORMAT_STOPWORDS})를 빼고 다시 ALL — "짱구 사진"은
 *       텍스트에 "사진"이 없어도 짱구 아이템이 나와야 한다</li>
 *   <li>그래도 0건이면 일부만 포함하는 결과(ANY)로 폴백 + {@code partialMatch=true}.
 *       형식 단어는 이미 빠져 있으므로 "사진"만 걸린 무관한 아이템이 섞이지 않는다</li>
 *   <li>그래도 0건이면 의미 검색(pgvector 임베딩,
 *       {@link ItemSemanticSearchService}) + {@code semanticMatch=true}. 오타·표현 차이는
 *       키워드로는 원리상 못 잡으므로(pg_trgm 유사도는 한국어에서 실측 실패) 이 단계가
 *       그 몫을 맡는다. 임베딩도 형식 단어를 뺀 문구로 만든다 — "짱구 사진"을 통째로
 *       임베딩하면 벡터가 "사진" 쪽으로 끌려가 무관한 사진 아이템이 잡힌다.
 *       키워드 총건수가 0이라는 판정은 페이지와 무관하니, 같은 검색어의
 *       모든 페이지가 같은 계층에서 나온다 — 페이지끼리 계층이 섞일 일은 없다</li>
 * </ol>
 *
 * <p><b>의미 검색 보충</b>: 키워드 결과가 있어도 <b>한 페이지를 못 채우면</b> 의미 검색
 * 결과를 뒤에 붙인다({@code semanticSupplementCount}). 키워드 1건("카페"가 텍스트에 있는
 * 아이템)이 의미상 관련한 아이템들(텍스트에 "카페"가 없는 카페들)을 통째로 가리는 문제를
 * 막는다 — 정확 일치는 여전히 맨 위이므로 특정 아이템을 제목으로 찾는 경우도 해치지 않는다.
 * 키워드 결과가 한 페이지를 넘으면 보충하지 않는다(이미 볼 게 많고, 임베딩 HTTP 호출을
 * 아낀다). 개념상 결과 목록은 [키워드 전부 ++ 보충 전부]로 고정이고 페이지는 그 위를
 * 자른다 — 키워드 총건수 판정이 페이지와 무관하므로 페이지끼리 순서가 섞이지 않는다.
 *
 * <p>클래스에 @Transactional이 없는 이유: 3단계가 임베딩 HTTP 호출(수백 ms~수 초)을 하는데,
 * 트랜잭션 안이면 그동안 DB 커넥션을 붙잡는다({@link ItemAiSearchService}가 LLM 호출을
 * 트랜잭션 밖에 두는 것과 같은 이유). 읽기 전용 조회들이라 각 리포지토리 호출이 각자
 * 커넥션을 쓰면 충분하다.
 */
@Service
public class ItemSearchService {

    /** 한 번에 조회 가능한 최대 건수. ItemService.MAX_PAGE_SIZE와 같은 정책이다. */
    private static final int MAX_PAGE_SIZE = 100;

    /** 검색어 길이 상한. 본문을 통째로 붙여넣는 식의 요청이 스캔을 무겁게 만드는 걸 막는다. */
    private static final int MAX_QUERY_LENGTH = 200;

    /**
     * 토큰 개수 상한. 토큰마다 ILIKE 조건이 하나씩 붙어 쿼리가 길어지므로 제한한다.
     * 넘치는 토큰은 버린다 — 앞쪽 단어가 대개 더 중요하다.
     */
    private static final int MAX_TOKENS = 5;

    /**
     * 매체·형식을 가리키는 단어들. "짱구 사진"의 "사진"은 찾는 대상(짱구)이 아니라 형태를
     * 말하는 토큰이라, 매칭 조건에 남기면 오히려 노이즈가 된다 — ANY 폴백에서 "사진"이
     * 텍스트에 있는 무관한 아이템이 걸리고, 임베딩 벡터도 "사진" 쪽으로 끌려간다.
     *
     * <p>단, 내용 단어가 하나도 안 남으면 빼지 않는다({@link #dropFormatStopwords}) —
     * "사진"만 친 사용자는 사진 아이템들을 훑고 싶은 것이다. 내용 단어가 있어도 1단계
     * (원문 그대로 ALL)는 형식 단어를 포함해 돌므로, "여권 사진"이라는 문구가 실제로 있는
     * 아이템은 여전히 최우선으로 잡힌다.
     */
    private static final Set<String> FORMAT_STOPWORDS = Set.of(
            // IMAGE 계열
            "사진", "이미지", "그림", "짤", "움짤", "스크린샷", "스샷", "캡처", "캡쳐",
            "photo", "image", "img", "pic",
            // URL 계열
            "링크", "주소", "사이트", "웹사이트", "홈페이지", "페이지",
            "글", "게시글", "게시물", "포스트", "포스팅", "기사",
            "영상", "동영상", "비디오", "url", "link", "video",
            // MEMO·문서 계열
            "메모", "노트", "파일", "문서", "자료", "memo", "note", "file");

    /**
     * 형식 단어 판정 전에 떼어보는 조사·접미. "사진을", "링크가", "스크린샷들"처럼 조사가
     * 붙은 채로 들어와도 잡기 위해서다. 뗀 나머지가 {@link #FORMAT_STOPWORDS}에 있을 때만
     * 형식 단어로 치므로, "한글"("글"로 끝남)이나 "구이"("이"로 끝남) 같은 내용 단어를
     * 잘못 자를 위험은 없다. 긴 접미부터 시도한다.
     */
    private static final List<String> STOPWORD_SUFFIXES = List.of(
            "들을", "들이", "들은", "들도", "들의", "으로", "에서", "이랑", "까지", "부터",
            "들", "이", "가", "을", "를", "은", "는", "의", "도", "만", "로", "와", "과", "랑");

    private final ItemSearchRepository itemSearchRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ItemSummaryAssembler itemSummaryAssembler;
    private final ItemSemanticSearchService itemSemanticSearchService;

    public ItemSearchService(ItemSearchRepository itemSearchRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ItemSummaryAssembler itemSummaryAssembler,
            ItemSemanticSearchService itemSemanticSearchService) {
        this.itemSearchRepository = itemSearchRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.itemSummaryAssembler = itemSummaryAssembler;
        this.itemSemanticSearchService = itemSemanticSearchService;
    }

    public ItemSearchResponse search(Long workspaceId, Long userId, String q, int page, int size) {
        verifyMembership(workspaceId, userId);

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));

        // 검색어가 비면 쿼리를 태우지 않는다. 패턴이 '%%'가 되어 워크스페이스 전체를 긁어오기
        // 때문인데, 사용자가 검색창을 비운 상황이라 에러보다 빈 결과가 자연스럽다.
        // 의미 검색도 안 태운다 — 빈 문장의 임베딩은 아무 의미가 없다.
        List<String> tokens = tokenize(q);
        if (tokens.isEmpty()) {
            return ItemSearchResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        // 형식 단어를 뺀 내용 토큰. 아래 폴백들과 의미 검색은 전부 이것만 쓴다.
        // 뺄 게 없거나 전부 형식 단어면 원래 토큰 그대로다.
        List<String> contentTokens = dropFormatStopwords(tokens);
        String semanticQuery = String.join(" ", contentTokens);

        // 1단계: 원문 토큰 전부 포함(ALL). 형식 단어까지 문구 그대로 있는 아이템이 최선이다.
        List<String> patterns = toLikePatterns(tokens);
        Page<Item> found = itemSearchRepository.search(workspaceId, patterns, MatchMode.ALL, pageable);
        MatchMode usedMode = MatchMode.ALL;

        // 2단계: 형식 단어를 빼고 다시 ALL — "짱구 사진"은 텍스트에 "사진"이 없어도
        // "짱구"만 있으면 잡혀야 한다. partialMatch로 치지 않는다 — 찾는 대상(내용 단어)은
        // 전부 일치했고, 형식 단어 제거는 매칭이 아니라 검색어 정규화다.
        if (found.getTotalElements() == 0 && contentTokens.size() < tokens.size()) {
            patterns = toLikePatterns(contentTokens);
            found = itemSearchRepository.search(workspaceId, patterns, MatchMode.ALL, pageable);
        }

        // 3단계: 일부만 포함(ANY) 폴백. 형식 단어는 이미 빠져 있으므로 "사진"만 걸린 무관한
        // 아이템은 여기 못 들어온다. 내용 단어가 하나뿐이면 2단계와 같은 쿼리라 돌 의미가 없다.
        boolean partialMatch = false;
        if (found.getTotalElements() == 0 && contentTokens.size() > 1) {
            patterns = toLikePatterns(contentTokens);
            found = itemSearchRepository.search(workspaceId, patterns, MatchMode.ANY, pageable);
            usedMode = MatchMode.ANY;
            partialMatch = found.getTotalElements() > 0;
        }

        // 문자가 일치하는 게 아예 없으면 의미 검색으로. 실패·결과 없음이면 null이라 그대로 0건.
        if (found.getTotalElements() == 0) {
            ItemListResponse semantic = itemSemanticSearchService.search(workspaceId, semanticQuery, pageable);
            if (semantic != null) {
                return ItemSearchResponse.fromSemantic(semantic);
            }
        }

        ItemListResponse list = itemSummaryAssembler.toListResponse(found);
        if (found.getTotalElements() == 0 || found.getTotalElements() > pageable.getPageSize()) {
            // 키워드만으로 페이지가 넘친다(또는 0건 폴백까지 실패했다) — 보충 없음.
            return ItemSearchResponse.from(list, partialMatch);
        }
        return supplemented(workspaceId, semanticQuery, pageable, found, usedMode, patterns, list, partialMatch);
    }

    /**
     * 키워드 결과(한 페이지 이내)에 의미 검색 보충을 붙인다. 개념상 [키워드 전부 ++ 보충
     * 전부]를 하나의 목록으로 보고 요청 페이지에 해당하는 구간을 채운다 — 키워드 총건수가
     * 페이지 크기 이하일 때만 오므로 키워드는 0페이지에 전부 들어 있고, 이후 페이지는 보충
     * 목록의 연속이다. 보충이 불가능하면(aimix 꺼짐/실패) 키워드 결과만 그대로 내보낸다.
     *
     * @param semanticQuery 임베딩에 쓸 문구 — 형식 단어를 뺀 내용 토큰들이다
     */
    private ItemSearchResponse supplemented(Long workspaceId, String semanticQuery, Pageable pageable,
            Page<Item> found, MatchMode usedMode, List<String> patterns,
            ItemListResponse list, boolean partialMatch) {
        // 중복 제거용 키워드 id 전체. 0페이지면 방금 조회한 내용이 곧 전체(총건수 ≤ 페이지
        // 크기)고, 뒷 페이지면 키워드 슬라이스가 비어 있으므로 0페이지를 다시 읽는다.
        List<Long> keywordIds = (pageable.getPageNumber() == 0 ? found
                : itemSearchRepository.search(workspaceId, patterns, usedMode,
                        PageRequest.of(0, pageable.getPageSize())))
                .getContent().stream().map(Item::getId).toList();

        int slotsLeft = pageable.getPageSize() - list.content().size();
        int supplementOffset = (int) Math.max(0, pageable.getOffset() - found.getTotalElements());
        ItemSemanticSearchService.Supplement supplement = itemSemanticSearchService.supplement(
                workspaceId, semanticQuery, keywordIds, slotsLeft, supplementOffset);
        if (supplement == null || supplement.totalElements() == 0) {
            return ItemSearchResponse.from(list, partialMatch);
        }

        List<ItemSummaryResponse> merged = new ArrayList<>(list.content());
        merged.addAll(supplement.content());
        return new ItemSearchResponse(merged, list.page(), list.size(),
                found.getTotalElements() + supplement.totalElements(),
                partialMatch, false, supplement.content().size());
    }

    /**
     * 검색어를 공백으로 쪼개 토큰 목록으로 만든다. 토큰 단위로 나누는 이유는 "파스타 을지로"
     * 처럼 순서가 다르거나 사이에 다른 말이 낀 경우도 잡기 위해서다 — 통짜 문자열로 찾으면
     * 정확히 그 구절이 있어야만 걸린다.
     */
    static List<String> tokenize(String q) {
        if (q == null) {
            return List.of();
        }
        String trimmed = q.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH);
        }
        return Arrays.stream(trimmed.split("\\s+"))
                .filter(token -> !token.isBlank())
                .limit(MAX_TOKENS)
                .toList();
    }

    /**
     * 형식 단어({@link #FORMAT_STOPWORDS})를 뺀 내용 토큰만 남긴다. 하나도 안 남으면
     * 원래 토큰을 그대로 돌려준다 — "사진"만 친 사용자에게 빈 결과를 줄 수는 없고,
     * 애초에 뺄 게 아니었다는 뜻이다(RuleBasedQueryPlanner가 전부 걸러졌을 때 원문으로
     * 되돌아가는 것과 같은 정책).
     */
    static List<String> dropFormatStopwords(List<String> tokens) {
        List<String> contentTokens = tokens.stream()
                .filter(token -> !isFormatStopword(token))
                .toList();
        return contentTokens.isEmpty() ? tokens : contentTokens;
    }

    /** 토큰 그대로, 또는 조사 하나를 뗀 나머지가 형식 단어인지. 대소문자는 무시한다(영어 단어용). */
    private static boolean isFormatStopword(String token) {
        String lower = token.toLowerCase();
        if (FORMAT_STOPWORDS.contains(lower)) {
            return true;
        }
        for (String suffix : STOPWORD_SUFFIXES) {
            if (lower.length() > suffix.length() && lower.endsWith(suffix)
                    && FORMAT_STOPWORDS.contains(lower.substring(0, lower.length() - suffix.length()))) {
                return true;
            }
        }
        return false;
    }

    static List<String> toLikePatterns(List<String> tokens) {
        return tokens.stream().map(ItemSearchService::toLikePattern).toList();
    }

    /**
     * LIKE 메타문자를 이스케이프한 부분일치 패턴. 이스케이프하지 않으면 사용자가 친 %가
     * 와일드카드로 동작해 전체 조회가 되고, _는 아무 글자에나 매칭된다.
     * PostgreSQL LIKE의 기본 이스케이프 문자가 백슬래시라 백슬래시부터 먼저 처리한다.
     */
    static String toLikePattern(String token) {
        String escaped = token
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private void verifyMembership(Long workspaceId, Long userId) {
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceAccessDeniedException(workspaceId));
    }
}
