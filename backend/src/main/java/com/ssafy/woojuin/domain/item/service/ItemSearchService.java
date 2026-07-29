package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSearchResponse;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.repository.ItemSearchRepository;
import com.ssafy.woojuin.domain.item.repository.MatchMode;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 검색(FR-030)의 키워드 계층 — 제목·AI 요약·본문(메모/추출 본문/OCR 텍스트)·미리보기
 * 설명을 한 번에 훑는다. 휴지통 아이템은 제외한다.
 *
 * <p><b>검색 단계 설계</b> (AI 모드 OFF일 때의 경로):
 * <ol>
 *   <li>모든 단어를 포함하는 결과(ALL) — 정확도 우선</li>
 *   <li>0건이면 일부만 포함하는 결과(ANY)로 폴백 + {@code partialMatch=true}</li>
 *   <li>그래도 0건이면 의미 검색(pgvector 임베딩) — <b>아직 미구현</b>. 임베딩 생성 파이프라인이
 *       올라온 뒤 {@code searchBySimilarity}를 이 자리에 끼운다. 오타·표현 차이는 키워드로는
 *       원리상 못 잡으므로(pg_trgm 유사도는 한국어에서 실측 실패) 이 단계가 그 몫을 맡는다</li>
 * </ol>
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

    private final ItemSearchRepository itemSearchRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ItemSummaryAssembler itemSummaryAssembler;

    public ItemSearchService(ItemSearchRepository itemSearchRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ItemSummaryAssembler itemSummaryAssembler) {
        this.itemSearchRepository = itemSearchRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.itemSummaryAssembler = itemSummaryAssembler;
    }

    @Transactional(readOnly = true)
    public ItemSearchResponse search(Long workspaceId, Long userId, String q, int page, int size) {
        verifyMembership(workspaceId, userId);

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));

        // 검색어가 비면 쿼리를 태우지 않는다. 패턴이 '%%'가 되어 워크스페이스 전체를 긁어오기
        // 때문인데, 사용자가 검색창을 비운 상황이라 에러보다 빈 결과가 자연스럽다.
        List<String> patterns = toLikePatterns(q);
        if (patterns.isEmpty()) {
            return ItemSearchResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        }

        Page<Item> found = itemSearchRepository.search(workspaceId, patterns, MatchMode.ALL, pageable);

        // 단어가 하나뿐이면 ALL과 ANY가 같은 쿼리라 폴백할 의미가 없다.
        boolean partialMatch = false;
        if (found.getTotalElements() == 0 && patterns.size() > 1) {
            found = itemSearchRepository.search(workspaceId, patterns, MatchMode.ANY, pageable);
            partialMatch = found.getTotalElements() > 0;
        }

        ItemListResponse list = itemSummaryAssembler.toListResponse(found);
        return ItemSearchResponse.from(list, partialMatch);
    }

    /**
     * 검색어를 공백으로 쪼개 토큰별 LIKE 패턴으로 만든다. 토큰 단위로 나누는 이유는 "파스타 을지로"
     * 처럼 순서가 다르거나 사이에 다른 말이 낀 경우도 잡기 위해서다 — 통짜 문자열로 찾으면
     * 정확히 그 구절이 있어야만 걸린다.
     */
    static List<String> toLikePatterns(String q) {
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
                .map(ItemSearchService::toLikePattern)
                .toList();
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
