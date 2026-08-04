package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.ai.AiMixClient;
import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSummaryResponse;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository.SimilarityRow;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 의미 기반 검색(pgvector) — 두 경로로 쓰인다({@link ItemSearchService} 참고):
 * <ul>
 *   <li><b>폴백</b>({@link #search}): 키워드(ALL→ANY)가 0건일 때 결과 전체를 대신한다</li>
 *   <li><b>보충</b>({@link #supplement}): 키워드 결과가 한 페이지를 못 채울 때, 키워드
 *       결과에 없는 아이템을 "비슷한 항목"으로 뒤에 붙인다 — 키워드 1건에 밀려 의미상
 *       관련한 아이템들이 통째로 묻히는 문제를 막는다("카페" 실측 사례)</li>
 * </ul>
 * 검색어를 임베딩해 아이템 임베딩(제목·요약·카테고리 평문, 임베딩 파이프라인이 저장)과
 * 코사인 거리로 비교하므로, 오타·표현 차이("이탈리안"→"파스타")를 문자 일치 없이 잡는다.
 *
 * <p>실패 정책은 임베딩 파이프라인과 동일 — aimix 미설정/다운/타임아웃이면 조용히 포기하고
 * null을 반환한다. 검색이 AI 사이드카 장애로 500이 나면 안 되고, 폴백이 없던 시절의 동작
 * (그냥 키워드 결과)이 자연스러운 축소이기 때문이다.
 *
 * <p>임베딩이 없는 아이템(AI 보강 실패로 요약 없음)은 여기 안 걸린다 — 백필
 * ({@code ItemEmbeddingBackfillRunner})이 요약 있는 아이템까지는 구제한다.
 */
@Service
public class ItemSemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(ItemSemanticSearchService.class);

    private final ObjectProvider<AiMixClient> aiMixClientProvider;
    private final ItemEmbeddingJdbcRepository embeddingRepository;
    private final ItemRepository itemRepository;
    private final ItemSummaryAssembler itemSummaryAssembler;
    private final double maxDistance;

    public ItemSemanticSearchService(ObjectProvider<AiMixClient> aiMixClientProvider,
            ItemEmbeddingJdbcRepository embeddingRepository,
            ItemRepository itemRepository,
            ItemSummaryAssembler itemSummaryAssembler,
            @Value("${woojuin.aimix.search-max-distance}") double maxDistance) {
        this.aiMixClientProvider = aiMixClientProvider;
        this.embeddingRepository = embeddingRepository;
        this.itemRepository = itemRepository;
        this.itemSummaryAssembler = itemSummaryAssembler;
        this.maxDistance = maxDistance;
    }

    /**
     * 검색어와 의미상 가까운 아이템을 거리순으로 찾는다.
     *
     * @return 목록 응답. 폴백이 불가능하거나(aimix 꺼짐/실패) 임계값 안에 아무것도 없으면
     *         null — 호출부는 키워드 0건 결과를 그대로 내보내면 된다
     */
    public ItemListResponse search(Long workspaceId, String q, Pageable pageable) {
        AiMixClient client = aiMixClientProvider.getIfAvailable();
        if (client == null) {
            return null;
        }
        try {
            // HTTP(임베딩)를 트랜잭션 밖에서 먼저 끝낸다 — 이 메서드에 @Transactional이
            // 없는 이유다(ItemAiSearchService가 LLM 호출을 밖에 두는 것과 같은 이유).
            float[] queryEmbedding = client.embedQuery(q);

            long total = embeddingRepository.countSimilar(workspaceId, queryEmbedding, maxDistance, List.of());
            if (total == 0) {
                log.debug("의미 검색: 임계값({}) 안에 결과 없음 — workspaceId={}", maxDistance, workspaceId);
                return null;
            }
            List<SimilarityRow> rows = embeddingRepository.searchBySimilarity(
                    workspaceId, queryEmbedding, maxDistance, List.of(), pageable.getPageSize(),
                    (int) pageable.getOffset());
            // 임계값 튜닝 근거를 남긴다 — 엉뚱한 결과가 잡히면 이 거리를 보고 상한을 내린다.
            log.info("의미 검색 폴백: workspaceId={}, {}건, 최근접 거리={}",
                    workspaceId, total, rows.isEmpty() ? null : rows.get(0).distance());
            return itemSummaryAssembler.toListResponse(
                    new PageImpl<>(toOrderedItems(rows), pageable, total));
        } catch (Exception e) {
            log.warn("의미 검색 폴백 실패 — 키워드 0건 그대로 응답: workspaceId={}", workspaceId, e);
            return null;
        }
    }

    /** 보충 검색 결과 — 이 페이지에 붙일 아이템들과, 제외분을 뺀 보충 전체 건수. */
    public record Supplement(List<ItemSummaryResponse> content, long totalElements) {
    }

    /**
     * 키워드 결과 <b>뒤에 붙일</b> 의미 검색 보충 — excludeItemIds(키워드로 이미 잡힌 것)를
     * 뺀 나머지를 거리순으로 돌려준다.
     *
     * @param limit  이 페이지에서 보충으로 채울 칸 수. 0이면 내용 조회 없이 건수만 센다 —
     *               페이지가 키워드로 꽉 찼어도 다음 페이지 존재 여부(totalElements)는 알아야 한다
     * @param offset 보충 목록 안에서의 시작 위치(페이지 경계를 넘어온 뒷 페이지용)
     * @return 보충 결과. 보충이 불가능하면(aimix 꺼짐/실패) null — 호출부는 키워드 결과만
     *         내보내면 된다. 임계값 안에 남는 게 없으면 totalElements=0인 빈 결과
     */
    public Supplement supplement(Long workspaceId, String q, List<Long> excludeItemIds,
            int limit, int offset) {
        AiMixClient client = aiMixClientProvider.getIfAvailable();
        if (client == null) {
            return null;
        }
        try {
            float[] queryEmbedding = client.embedQuery(q);

            long total = embeddingRepository.countSimilar(
                    workspaceId, queryEmbedding, maxDistance, excludeItemIds);
            if (total == 0 || limit <= 0) {
                return new Supplement(List.of(), total);
            }
            List<SimilarityRow> rows = embeddingRepository.searchBySimilarity(
                    workspaceId, queryEmbedding, maxDistance, excludeItemIds, limit, offset);
            log.info("의미 검색 보충: workspaceId={}, 보충 {}건(전체 {}), 최근접 거리={}",
                    workspaceId, rows.size(), total,
                    rows.isEmpty() ? null : rows.get(0).distance());
            // PageImpl은 assembler가 요구하는 형태를 맞추기 위한 껍데기다 — 페이지 정보는
            // 호출부(ItemSearchService)가 키워드 결과와 합쳐서 다시 계산한다.
            return new Supplement(
                    itemSummaryAssembler.toListResponse(new PageImpl<>(toOrderedItems(rows))).content(),
                    total);
        } catch (Exception e) {
            log.warn("의미 검색 보충 실패 — 키워드 결과만 응답: workspaceId={}", workspaceId, e);
            return null;
        }
    }

    /** id로 로드한 아이템을 거리순(rows 순서)으로 재배열한다 — findAllById는 순서를 보장하지 않는다. */
    private List<Item> toOrderedItems(List<SimilarityRow> rows) {
        Map<Long, Item> byId = new HashMap<>();
        for (Item item : itemRepository.findAllById(rows.stream().map(SimilarityRow::itemId).toList())) {
            byId.put(item.getId(), item);
        }
        return rows.stream()
                .map(row -> byId.get(row.itemId()))
                .filter(item -> item != null)
                .toList();
    }
}
