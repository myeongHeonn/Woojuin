package com.ssafy.woojuin.domain.item.repository;

import com.ssafy.woojuin.domain.item.entity.Item;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * 통합 검색(FR-030)의 키워드 계층. 의미 검색(pgvector)은 이 계층이 0건일 때 도는 별도 폴백으로
 * 붙을 예정이라 여기서는 다루지 않는다.
 *
 * <p><b>텍스트 인덱스 없이 워크스페이스 범위를 훑는다.</b> 아이템은 항상 워크스페이스 단위로만
 * 검색되고 한 워크스페이스의 아이템은 많아야 1000건 수준이라, {@code idx_items_ws_active}로
 * 범위를 좁힌 뒤 훑는 쪽이 모든 면에서 낫다. 실측(워크스페이스 1000건 / 본문 3.1MB):
 *
 * <ul>
 *   <li>검색 829ms → 60ms — tsvector·트라이그램 GIN을 두면 워크스페이스 필터가 훨씬 선택적이라
 *       플래너가 GIN을 아예 안 쓰면서, 행마다 {@code to_tsvector}를 계산하는 비용만 낸다</li>
 *   <li>AI 보강 UPDATE 1000건 1066ms → 16ms — GIN 갱신 비용이 사라진다</li>
 *   <li>인덱스 89MB → 0</li>
 *   <li>2글자 검색어의 성능 절벽이 사라진다(트라이그램은 2글자에서 인덱스를 못 탄다)</li>
 * </ul>
 *
 * <p>tsvector를 버려도 검색 결과는 오히려 넓어진다 — 어떤 문서에 토큰으로 존재하는 단어는
 * 부분문자열로도 반드시 존재하므로 ILIKE가 상위집합이고, 토큰 경계에도 얽매이지 않는다
 * ("파스타"로 "파스타집을" 매칭 — tsvector가 못 하던 것).
 *
 * <p>쿼리를 동적으로 조립하는 이유는 토큰 개수가 가변이라서다. 토큰은 전부 바인딩 파라미터로
 * 넘기므로 SQL 인젝션 여지는 없다.
 */
@Repository
public class ItemSearchRepository {

    /** 검색 대상 — 제목 + AI 요약 + 본문(메모 원문/URL 추출 본문/이미지 OCR) + 미리보기 설명. */
    private static final String SEARCH_TEXT =
            "(coalesce(i.title, '') || ' ' || coalesce(i.summary, '') || ' '"
                    + " || coalesce(i.content, '') || ' ' || coalesce(i.preview_description, ''))";

    private static final String BASE_WHERE = "i.workspace_id = :workspaceId AND i.deleted_at IS NULL";

    private final EntityManager entityManager;

    public ItemSearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * @param likePatterns 이미 이스케이프되어 {@code %토큰%} 형태로 감싸진 패턴들. 비어 있으면 안 된다
     *                     (호출부가 먼저 걸러야 함).
     * @param mode         모든 토큰이 맞아야 하는지(ALL), 하나만 맞아도 되는지(ANY)
     */
    public Page<Item> search(Long workspaceId, List<String> likePatterns, MatchMode mode, Pageable pageable) {
        if (likePatterns.isEmpty()) {
            throw new IllegalArgumentException("검색 토큰이 비어 있습니다");
        }

        String where = BASE_WHERE + " AND " + textCondition(likePatterns, mode);

        Query countQuery = entityManager.createNativeQuery("SELECT count(*) FROM items i WHERE " + where);
        bind(countQuery, workspaceId, likePatterns);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        Query query = entityManager.createNativeQuery(
                "SELECT i.* FROM items i WHERE " + where + " ORDER BY " + orderBy(likePatterns), Item.class);
        bind(query, workspaceId, likePatterns);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Item> content = query.getResultList();
        return new PageImpl<>(content, pageable, total);
    }

    private String textCondition(List<String> likePatterns, MatchMode mode) {
        return joinMatches(SEARCH_TEXT, likePatterns, mode == MatchMode.ALL ? " AND " : " OR ");
    }

    /**
     * 관련도 정렬 — 맞은 토큰이 많은 순이 우선이고(ANY 폴백에서 의미가 있다), 같으면 제목에
     * 걸린 것, 그다음 요약, 마지막이 본문뿐인 것. 동점은 최신순.
     *
     * <p>ts_rank를 버린 대신 쓰는 단순 휴리스틱인데, 사용자가 기억하는 건 대개 제목이라
     * 실제 체감은 오히려 낫다.
     */
    private String orderBy(List<String> likePatterns) {
        return matchedTokenCount(likePatterns) + " DESC,"
                + " CASE WHEN " + joinMatches("coalesce(i.title, '')", likePatterns, " AND ") + " THEN 0"
                + " WHEN " + joinMatches("coalesce(i.summary, '')", likePatterns, " AND ") + " THEN 1"
                + " ELSE 2 END,"
                + " i.created_at DESC";
    }

    private String matchedTokenCount(List<String> likePatterns) {
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < likePatterns.size(); i++) {
            terms.add("(CASE WHEN " + SEARCH_TEXT + " ILIKE :p" + i + " THEN 1 ELSE 0 END)");
        }
        return "(" + String.join(" + ", terms) + ")";
    }

    private String joinMatches(String expression, List<String> likePatterns, String operator) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < likePatterns.size(); i++) {
            parts.add(expression + " ILIKE :p" + i);
        }
        return "(" + String.join(operator, parts) + ")";
    }

    private void bind(Query query, Long workspaceId, List<String> likePatterns) {
        query.setParameter("workspaceId", workspaceId);
        for (int i = 0; i < likePatterns.size(); i++) {
            query.setParameter("p" + i, likePatterns.get(i));
        }
    }
}
