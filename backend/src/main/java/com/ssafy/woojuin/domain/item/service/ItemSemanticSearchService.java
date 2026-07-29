package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.ai.AiMixClient;
import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
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
 * 의미 기반 검색(pgvector) — 키워드 검색(ALL→ANY)이 0건일 때만 도는 폴백이다
 * ({@link ItemSearchService} 참고). 검색어를 임베딩해 아이템 임베딩(카테고리+제목+요약,
 * 임베딩 파이프라인이 저장)과 코사인 거리로 비교하므로, 오타·표현 차이("이탈리안"→"파스타")를
 * 문자 일치 없이 잡는다.
 *
 * <p>실패 정책은 임베딩 파이프라인과 동일 — aimix 미설정/다운/타임아웃이면 조용히 포기하고
 * null을 반환한다. 검색이 AI 사이드카 장애로 500이 나면 안 되고, 폴백이 없던 시절의 동작
 * (그냥 0건)이 자연스러운 축소이기 때문이다.
 *
 * <p>임베딩이 없는 아이템(도입 전 저장, AI 보강 실패로 요약 없음)은 여기 안 걸린다 —
 * 백필을 하지 않기로 한 결정과 일관된 한계다.
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

            long total = embeddingRepository.countSimilar(workspaceId, queryEmbedding, maxDistance);
            if (total == 0) {
                log.debug("의미 검색: 임계값({}) 안에 결과 없음 — workspaceId={}", maxDistance, workspaceId);
                return null;
            }
            List<SimilarityRow> rows = embeddingRepository.searchBySimilarity(
                    workspaceId, queryEmbedding, maxDistance, pageable.getPageSize(),
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
