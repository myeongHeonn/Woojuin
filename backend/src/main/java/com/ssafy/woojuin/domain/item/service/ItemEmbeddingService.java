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
import com.ssafy.woojuin.global.sse.WorkspaceChangedEvent;
import com.ssafy.woojuin.global.sse.WorkspaceEventType;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 아이템 임베딩 저장 + 워크스페이스 3차원 좌표 재계산.
 *
 * <p>가공 파이프라인이 끝난 뒤(트랜잭션 커밋 후) 디스패처가 호출한다 — 임베딩·UMAP은
 * 느린 HTTP 호출이라 아이템 트랜잭션(DB 커넥션)을 붙잡은 채 돌리지 않는다.
 *
 * <p>컨슈머가 병렬(consumer-count)이라 같은 워크스페이스의 좌표 재계산이 <b>동시에 겹칠 수
 * 있다</b> — 나중 커밋이 이기고, 진행 중이던 재계산이 방금 들어온 벡터를 못 본 채 끝나면
 * 그 아이템 좌표는 다음 재계산 때 잡힌다. 좌표는 우주 뷰 위치라는 부가 정보고 아이템이
 * 저장될 때마다 다시 계산되므로 잠깐의 어긋남은 수용한다(락으로 직렬화할 가치가 없다).
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
    private final ApplicationEventPublisher eventPublisher;

    public ItemEmbeddingService(ObjectProvider<AiMixClient> aiMixClientProvider,
            ItemRepository itemRepository, ItemCategoryRepository itemCategoryRepository,
            CategoryRepository categoryRepository, ItemEmbeddingJdbcRepository embeddingRepository,
            ApplicationEventPublisher eventPublisher) {
        this.aiMixClientProvider = aiMixClientProvider;
        this.itemRepository = itemRepository;
        this.itemCategoryRepository = itemCategoryRepository;
        this.categoryRepository = categoryRepository;
        this.embeddingRepository = embeddingRepository;
        this.eventPublisher = eventPublisher;
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
        if (!hasEmbeddableSourceText(item)) {
            log.debug("본문·미리보기가 모두 비어 임베딩 생략(요약이 제목만으로 지어진 것): itemId={}", itemId);
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
        // 좌표가 준비된 지금이 우주 뷰 입장에서 진짜 "바뀐" 시점이다. processor.process()
        // 단계에서 이미 ITEM 신호가 한 번 나갔지만(가공 완료 알림), 그때는 아직 이 아이템의
        // 좌표가 없어 프론트가 재조회해도 별이 안 보인다 — 여기서 한 번 더 알려야 한다.
        eventPublisher.publishEvent(
                WorkspaceChangedEvent.of(item.getWorkspaceId(), WorkspaceEventType.ITEM));
        log.info("임베딩·좌표 갱신 완료: itemId={}, workspaceId={}", itemId, item.getWorkspaceId());
    }

    /**
     * 임베딩할 텍스트 신호가 있는가 — 본문(메모 원문/URL 추출 본문/이미지 OCR·설명)이나
     * 미리보기 설명 중 하나는 있어야 한다. 둘 다 없으면 AI 요약은 제목(대개 생 URL)만 보고
     * 지어낸 무의미한 문장이고, 그 임베딩은 벡터 공간 중간쯤에 떠서 아무 검색어에나
     * 임계값 안으로 걸린다(실측: 크롤링 실패한 스마트스토어 상품이 "카페" 검색에 0.67로
     * 등장). 요약 텍스트의 실패 패턴을 감지하는 대신 입력 신호로 판정하는 이유는 LLM
     * 출력 문구가 언제든 바뀔 수 있어서다.
     *
     * <p>백필 대상 조회({@code ItemRepository#findEmbeddingSourceRows})와 기존 임베딩
     * 정리({@code ItemEmbeddingJdbcRepository#deleteEmbeddingsWithoutSourceText})의 SQL
     * 조건도 이 규칙과 같아야 한다.
     */
    static boolean hasEmbeddableSourceText(Item item) {
        return (item.getContent() != null && !item.getContent().isBlank())
                || (item.getPreviewDescription() != null && !item.getPreviewDescription().isBlank());
    }

    /**
     * 워크스페이스 전체 임베딩으로 3차원 좌표를 다시 계산해 반영한다.
     * 패키지 공개인 이유: {@link ItemEmbeddingBackfillRunner}가 워크스페이스 벌크 갱신 뒤
     * 1회 호출로 재사용한다(아이템별 재계산 경로를 타면 UMAP이 아이템 수만큼 돈다).
     */
    void recomputeCoordinates(AiMixClient client, Long workspaceId) {
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
