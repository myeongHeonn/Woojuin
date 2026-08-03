package com.ssafy.woojuin.domain.item.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.ai.AiMixClient;
import com.ssafy.woojuin.domain.ai.AiMixClient.ItemEmbeddingResult;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.entity.ItemCategory;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import com.ssafy.woojuin.domain.category.repository.ItemCategoryRepository;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingSourceRow;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemEmbeddingBackfillRunnerTest {

    @Mock AiMixClient client;
    @Mock ItemRepository itemRepository;
    @Mock ItemCategoryRepository itemCategoryRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ItemEmbeddingJdbcRepository embeddingRepository;
    @Mock ItemEmbeddingService itemEmbeddingService;

    private ItemEmbeddingBackfillRunner runnerWith(AiMixClient available) {
        @SuppressWarnings("unchecked")
        ObjectProvider<AiMixClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(available);
        return new ItemEmbeddingBackfillRunner(provider, itemRepository, itemCategoryRepository,
                categoryRepository, embeddingRepository, itemEmbeddingService);
    }

    private void givenCategories(List<Long> itemIds) {
        List<ItemCategory> links = itemIds.stream()
                .map(itemId -> ItemCategory.builder().itemId(itemId).categoryId(7L).build())
                .toList();
        Category category = Category.builder().workspaceId(1L).name("생활·할 일").build();
        ReflectionTestUtils.setField(category, "id", 7L);
        when(itemCategoryRepository.findByItemIdIn(itemIds)).thenReturn(links);
        when(categoryRepository.findAllById(List.of(7L))).thenReturn(List.of(category));
    }

    @Test
    void 해시가_바뀐_아이템만_업서트하고_좌표는_워크스페이스당_한_번_재계산한다() {
        when(itemRepository.findWorkspaceIdsWithEmbeddableItems()).thenReturn(List.of(1L));
        when(itemRepository.findEmbeddingSourceRows(1L)).thenReturn(List.of(
                new ItemEmbeddingSourceRow(10L, "제목10", "요약10"),
                new ItemEmbeddingSourceRow(11L, "제목11", "요약11")));
        givenCategories(List.of(10L, 11L));
        when(embeddingRepository.findInputHashes(anyCollection()))
                .thenReturn(Map.of(10L, "sha256:v2-already", 11L, "sha256:v1-old"));
        when(client.createEmbeddings(anyList())).thenReturn(List.of(
                new ItemEmbeddingResult(10L, "model", "sha256:v2-already", new float[] {0.1f}),
                new ItemEmbeddingResult(11L, "model", "sha256:v2-new", new float[] {0.2f})));

        runnerWith(client).run();

        verify(embeddingRepository).upsert(eq(11L), eq(1L), any(), anyString(), eq("sha256:v2-new"));
        verify(embeddingRepository, never()).upsert(eq(10L), anyLong(), any(), anyString(), anyString());
        verify(itemEmbeddingService).recomputeCoordinates(client, 1L);
    }

    /** at-least-once 재실행 — 전부 새 포맷이면 저장도 좌표 재계산도 다시 하지 않는다. */
    @Test
    void 전부_무변경이면_업서트와_재계산을_건너뛴다() {
        when(itemRepository.findWorkspaceIdsWithEmbeddableItems()).thenReturn(List.of(1L));
        when(itemRepository.findEmbeddingSourceRows(1L)).thenReturn(List.of(
                new ItemEmbeddingSourceRow(10L, "제목", "요약")));
        givenCategories(List.of(10L));
        when(embeddingRepository.findInputHashes(anyCollection()))
                .thenReturn(Map.of(10L, "sha256:same"));
        when(client.createEmbeddings(anyList())).thenReturn(List.of(
                new ItemEmbeddingResult(10L, "model", "sha256:same", new float[] {0.1f})));

        runnerWith(client).run();

        verify(embeddingRepository, never()).upsert(anyLong(), anyLong(), any(), anyString(), anyString());
        verify(itemEmbeddingService, never()).recomputeCoordinates(any(), anyLong());
    }

    /** 건별 임베딩과 같은 제외 조건 — 카테고리 없는 아이템은 계약(1개 이상)을 못 채운다. */
    @Test
    void 카테고리_없는_아이템만_있으면_임베딩_호출_없이_끝난다() {
        when(itemRepository.findWorkspaceIdsWithEmbeddableItems()).thenReturn(List.of(1L));
        when(itemRepository.findEmbeddingSourceRows(1L)).thenReturn(List.of(
                new ItemEmbeddingSourceRow(10L, "제목", "요약")));
        when(itemCategoryRepository.findByItemIdIn(List.of(10L))).thenReturn(List.of());

        runnerWith(client).run();

        verify(client, never()).createEmbeddings(anyList());
        verify(itemEmbeddingService, never()).recomputeCoordinates(any(), anyLong());
    }

    @Test
    void 한_워크스페이스가_실패해도_다음_워크스페이스는_계속한다() {
        when(itemRepository.findWorkspaceIdsWithEmbeddableItems()).thenReturn(List.of(1L, 2L));
        when(itemRepository.findEmbeddingSourceRows(1L))
                .thenThrow(new IllegalStateException("db down"));
        when(itemRepository.findEmbeddingSourceRows(2L)).thenReturn(List.of(
                new ItemEmbeddingSourceRow(20L, "제목", "요약")));
        givenCategories(List.of(20L));
        when(embeddingRepository.findInputHashes(anyCollection())).thenReturn(Map.of());
        when(client.createEmbeddings(anyList())).thenReturn(List.of(
                new ItemEmbeddingResult(20L, "model", "sha256:new", new float[] {0.1f})));

        runnerWith(client).run();

        verify(embeddingRepository).upsert(eq(20L), eq(2L), any(), anyString(), eq("sha256:new"));
    }

    @Test
    void aimix가_꺼져_있으면_아무것도_하지_않는다() {
        runnerWith(null).run();

        verify(itemRepository, never()).findWorkspaceIdsWithEmbeddableItems();
    }
}
