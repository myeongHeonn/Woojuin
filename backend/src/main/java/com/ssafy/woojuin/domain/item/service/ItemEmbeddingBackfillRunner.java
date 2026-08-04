package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.ai.AiMixClient;
import com.ssafy.woojuin.domain.ai.AiMixClient.EmbeddingCategory;
import com.ssafy.woojuin.domain.ai.AiMixClient.EmbeddingSource;
import com.ssafy.woojuin.domain.ai.AiMixClient.ItemEmbeddingResult;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.entity.ItemCategory;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import com.ssafy.woojuin.domain.category.repository.ItemCategoryRepository;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingSourceRow;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 임베딩 텍스트 포맷이 바뀌었을 때(예: category-title-summary-v1 → title-summary-categories-v2)
 * 저장된 벡터 전체를 새 포맷으로 다시 만드는 일회성 백필. 포맷을 바꾸고 백필 없이 두면
 * 새 아이템(v2)과 옛 아이템(v1)이 한 벡터 공간에 섞여 옛 아이템일수록 검색에서 밀려나는
 * 편향이 생긴다 — 포맷 변경과 이 백필은 한 세트다.
 *
 * <p>AIMIX_EMBEDDING_BACKFILL=true로 기동하면 기동 완료 후 백그라운드 스레드에서 한 번
 * 돌고 끝난다({@code CategoryColorBackfill}과 같은 자가치유 패턴, 단 느린 외부 HTTP가
 * 껴 있어 기동 스레드 대신 데몬 스레드에서 돈다). ai-mix가 계산한 input_hash가 저장값과
 * 같으면 업서트를 건너뛰므로 중간에 죽어도 재실행이 안전하다(멱등). 임베딩 API 호출
 * 자체는 재실행 때 다시 나간다 — 해시는 텍스트 조립과 함께 ai-mix 소유라 백엔드가 호출
 * 전에 미리 알 수 없다. 비용은 text-embedding-3-small 기준 수천 건에 몇 센트 수준.
 *
 * <p>요약은 있는데 벡터가 없던 아이템(임베딩 시점 일시 장애로 누락)도 이 경로로 함께
 * 구제된다. 요약 자체가 없는 아이템은 대상이 아니다 — 임베딩이 아니라 AI 보강 재시도가
 * 필요한 건이다.
 *
 * <p>운영 중 컨슈머의 건별 임베딩({@link ItemEmbeddingService})과 겹칠 수 있으나 같은
 * 입력이면 같은 벡터라 어느 쪽이 이겨도 무해하다(last-write-wins, 좌표 재계산 동시성은
 * {@link ItemEmbeddingService} javadoc과 같은 논리로 수용).
 *
 * <p>백필이 끝나면 플래그를 다시 꺼둘 것 — 켜두면 기동마다 전체 임베딩 API 호출이 반복된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "woojuin.aimix.embedding-backfill", havingValue = "true")
public class ItemEmbeddingBackfillRunner {

    private static final int BATCH_SIZE = 100;   // ai-mix /v1/embeddings/batch 계약 상한

    private final ObjectProvider<AiMixClient> aiMixClientProvider;
    private final ItemRepository itemRepository;
    private final ItemCategoryRepository itemCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final ItemEmbeddingJdbcRepository embeddingRepository;
    private final ItemEmbeddingService itemEmbeddingService;

    public ItemEmbeddingBackfillRunner(ObjectProvider<AiMixClient> aiMixClientProvider,
            ItemRepository itemRepository, ItemCategoryRepository itemCategoryRepository,
            CategoryRepository categoryRepository, ItemEmbeddingJdbcRepository embeddingRepository,
            ItemEmbeddingService itemEmbeddingService) {
        this.aiMixClientProvider = aiMixClientProvider;
        this.itemRepository = itemRepository;
        this.itemCategoryRepository = itemCategoryRepository;
        this.categoryRepository = categoryRepository;
        this.embeddingRepository = embeddingRepository;
        this.itemEmbeddingService = itemEmbeddingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread worker = new Thread(this::run, "embedding-backfill");
        worker.setDaemon(true);
        worker.start();
    }

    void run() {
        AiMixClient client = aiMixClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("임베딩 백필: aimix가 꺼져 있어 건너뜁니다(AIMIX_ENABLED=false)");
            return;
        }
        // 적격 규칙(본문·미리보기 중 하나는 있어야 함) 도입 전에 저장된 무의미한 벡터 청소.
        // 좌표 재계산은 안 한다 — 남은 벡터의 3차원 배치가 살짝 낡을 뿐이고, 다음 아이템
        // 저장 때 어차피 전체 재계산된다.
        int deleted = embeddingRepository.deleteEmbeddingsWithoutSourceText();
        if (deleted > 0) {
            log.info("임베딩 백필: 텍스트 신호 없는 아이템의 임베딩 {}건 제거", deleted);
        }

        List<Long> workspaceIds = itemRepository.findWorkspaceIdsWithEmbeddableItems();
        log.info("임베딩 백필 시작: {}개 워크스페이스", workspaceIds.size());
        int updatedTotal = 0;
        int failedWorkspaces = 0;
        for (Long workspaceId : workspaceIds) {
            try {
                updatedTotal += backfillWorkspace(client, workspaceId);
            } catch (Exception e) {
                failedWorkspaces++;
                log.warn("임베딩 백필 실패(다음 워크스페이스 계속): workspaceId={}, cause={}",
                        workspaceId, e.toString());
            }
        }
        log.info("임베딩 백필 완료: {}개 워크스페이스(실패 {}), 총 {}건 갱신",
                workspaceIds.size(), failedWorkspaces, updatedTotal);
    }

    private int backfillWorkspace(AiMixClient client, Long workspaceId) {
        List<ItemEmbeddingSourceRow> rows = itemRepository.findEmbeddingSourceRows(workspaceId);
        Map<Long, List<EmbeddingCategory>> categoriesByItem = loadCategories(rows);
        List<EmbeddingSource> sources = rows.stream()
                .filter(row -> categoriesByItem.containsKey(row.itemId()))
                .map(row -> new EmbeddingSource(row.itemId(), row.title(), row.summary(),
                        categoriesByItem.get(row.itemId())))
                .toList();
        int noCategory = rows.size() - sources.size();
        if (noCategory > 0) {
            // 건별 임베딩과 같은 제외 조건(카테고리 1개 이상 필수) — 조용히 빠지면 "빠짐없이
            // 다시 만들었다"로 읽히므로 수를 남긴다.
            log.info("임베딩 백필: 카테고리 없는 아이템 {}건 제외: workspaceId={}", noCategory, workspaceId);
        }
        if (sources.isEmpty()) {
            return 0;
        }

        Map<Long, String> storedHashes = embeddingRepository.findInputHashes(
                sources.stream().map(EmbeddingSource::itemId).toList());
        int updated = 0;
        for (int from = 0; from < sources.size(); from += BATCH_SIZE) {
            List<EmbeddingSource> batch =
                    sources.subList(from, Math.min(from + BATCH_SIZE, sources.size()));
            for (ItemEmbeddingResult result : client.createEmbeddings(batch)) {
                if (result.inputHash() != null
                        && result.inputHash().equals(storedHashes.get(result.itemId()))) {
                    continue;   // 이미 새 포맷(재실행·운영 중 건별 갱신과의 경합) — 그대로 둔다
                }
                embeddingRepository.upsert(result.itemId(), workspaceId,
                        result.embedding(), result.model(), result.inputHash());
                updated++;
            }
        }
        if (updated > 0) {
            itemEmbeddingService.recomputeCoordinates(client, workspaceId);
        }
        log.info("임베딩 백필: workspaceId={}, 대상 {}건, 갱신 {}건", workspaceId, sources.size(), updated);
        return updated;
    }

    private Map<Long, List<EmbeddingCategory>> loadCategories(List<ItemEmbeddingSourceRow> rows) {
        List<Long> itemIds = rows.stream().map(ItemEmbeddingSourceRow::itemId).toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        List<ItemCategory> links = itemCategoryRepository.findByItemIdIn(itemIds);
        Map<Long, String> categoryNames = categoryRepository.findAllById(
                        links.stream().map(ItemCategory::getCategoryId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, List<EmbeddingCategory>> byItem = new HashMap<>();
        for (ItemCategory link : links) {
            String name = categoryNames.get(link.getCategoryId());
            if (name == null) {
                continue;
            }
            byItem.computeIfAbsent(link.getItemId(), key -> new ArrayList<>())
                    .add(new EmbeddingCategory(link.getCategoryId(), name));
        }
        return byItem;
    }
}
