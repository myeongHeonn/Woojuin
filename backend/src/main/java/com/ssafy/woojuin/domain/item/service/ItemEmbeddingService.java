package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.ai.AiMixClient;
import com.ssafy.woojuin.domain.ai.AiMixClient.EmbeddingCategory;
import com.ssafy.woojuin.domain.ai.AiMixClient.EmbeddingResult;
import com.ssafy.woojuin.domain.ai.AiMixClient.ItemPoint;
import com.ssafy.woojuin.domain.ai.AiMixClient.ItemVector;
import com.ssafy.woojuin.domain.category.entity.ItemCategory;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import com.ssafy.woojuin.domain.category.repository.ItemCategoryRepository;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 아이템 임베딩 저장 + 워크스페이스 3차원 좌표 재계산.
 *
 * <p>가공 파이프라인이 끝난 뒤(트랜잭션 커밋 후) 디스패처가 호출한다 — 임베딩·UMAP은
 * 느린 HTTP 호출이라 아이템 트랜잭션(DB 커넥션)을 붙잡은 채 돌리지 않는다. 컨슈머가
 * 순차 처리라 per-item 지연이 늘 뿐 부하가 겹치진 않는다.
 *
 * <p>임베딩 입력은 ai-mix 계약(제목+요약+카테고리)을 따르므로 <b>요약이 있어야</b> 한다 —
 * AI 보강까지 실패한 아이템은 임베딩 없이 남고 우주 뷰에서 빠진다. 저장된 input_hash와
 * 같으면(재배달·내용 무변경) 재계산 전체를 건너뛴다.
 *
 * <p>모든 실패를 흡수한다 — 임베딩·좌표는 부가 정보라, 이미 DONE으로 저장된 아이템을
 * 재시도 루프에 다시 넣을 이유가 없다. 벡터는 이후 의미 기반 검색(pgvector)에도 쓰인다.
 */
@Slf4j
@Service
public class ItemEmbeddingService {

    private final ObjectProvider<AiMixClient> aiMixClientProvider;
    private final ItemRepository itemRepository;
    private final ItemCategoryRepository itemCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final ItemEmbeddingJdbcRepository embeddingRepository;

    public ItemEmbeddingService(ObjectProvider<AiMixClient> aiMixClientProvider,
            ItemRepository itemRepository, ItemCategoryRepository itemCategoryRepository,
            CategoryRepository categoryRepository, ItemEmbeddingJdbcRepository embeddingRepository) {
        this.aiMixClientProvider = aiMixClientProvider;
        this.itemRepository = itemRepository;
        this.itemCategoryRepository = itemCategoryRepository;
        this.categoryRepository = categoryRepository;
        this.embeddingRepository = embeddingRepository;
    }

    /** 아이템 가공 완료 직후 호출된다. 어떤 실패도 밖으로 새지 않는다. */
    public void onItemProcessed(Long itemId) {
        AiMixClient client = aiMixClientProvider.getIfAvailable();
        if (client == null) {
            return;   // aimix 꺼짐 — 요약·분류와 같은 스위치로 임베딩도 꺼진다
        }
        try {
            embedAndRecompute(client, itemId);
        } catch (Exception e) {
            log.warn("임베딩·좌표 갱신 실패(무시): itemId={}, cause={}", itemId, e.toString());
        }
    }

    private void embedAndRecompute(AiMixClient client, Long itemId) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null || item.getDeletedAt() != null) {
            return;
        }
        if (item.getSummary() == null || item.getSummary().isBlank()) {
            log.debug("요약이 없어 임베딩 생략(AI 보강 실패 아이템): itemId={}", itemId);
            return;
        }
        List<EmbeddingCategory> categories = categoriesOf(itemId);
        if (categories.isEmpty()) {
            return;   // 기타 폴백조차 없는 옛 워크스페이스 — 계약상 카테고리 1개 이상 필수
        }

        EmbeddingResult result = client.createEmbedding(
                itemId, item.getTitle(), item.getSummary(), categories);
        if (embeddingRepository.findInputHash(itemId)
                .filter(stored -> stored.equals(result.inputHash())).isPresent()) {
            log.debug("입력 무변경, 임베딩·재계산 생략: itemId={}", itemId);
            return;
        }

        embeddingRepository.upsert(itemId, item.getWorkspaceId(),
                result.embedding(), result.model(), result.inputHash());
        recomputeCoordinates(client, item.getWorkspaceId());
        log.info("임베딩·좌표 갱신 완료: itemId={}, workspaceId={}", itemId, item.getWorkspaceId());
    }

    /** 워크스페이스 전체 임베딩으로 3차원 좌표를 다시 계산해 반영한다. */
    private void recomputeCoordinates(AiMixClient client, Long workspaceId) {
        List<ItemVector> vectors = embeddingRepository.findActiveVectors(workspaceId);
        if (vectors.isEmpty()) {
            return;
        }
        List<ItemPoint> points = client.reduceCoordinates(vectors);
        List<Object[]> updates = points.stream()
                .map(point -> new Object[] {point.x(), point.y(), point.z(), point.itemId()})
                .toList();
        embeddingRepository.updateCoordinates(updates);
    }

    private List<EmbeddingCategory> categoriesOf(Long itemId) {
        List<Long> categoryIds = itemCategoryRepository.findByItemId(itemId).stream()
                .map(ItemCategory::getCategoryId)
                .toList();
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return categoryRepository.findAllById(categoryIds).stream()
                .map(category -> new EmbeddingCategory(category.getId(), category.getName()))
                .toList();
    }
}
