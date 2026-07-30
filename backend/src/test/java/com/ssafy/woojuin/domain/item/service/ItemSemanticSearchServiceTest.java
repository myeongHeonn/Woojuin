package com.ssafy.woojuin.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.ai.AiMixClient;
import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository.SimilarityRow;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemSemanticSearchServiceTest {

    private static final float[] QUERY_EMBEDDING = {0.1f, 0.2f};

    @Mock AiMixClient client;
    @Mock ItemEmbeddingJdbcRepository embeddingRepository;
    @Mock ItemRepository itemRepository;
    @Mock com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService itemCategoryQueryService;
    @Mock S3Uploader s3Uploader;

    private ItemSemanticSearchService service;

    @BeforeEach
    void setUp() {
        service = serviceWith(client);
    }

    private ItemSemanticSearchService serviceWith(AiMixClient aiMixClient) {
        @SuppressWarnings("unchecked")
        ObjectProvider<AiMixClient> provider = mock(ObjectProvider.class);
        // aimix 꺼짐 테스트는 setUp의 서비스를 안 쓰므로 이 스텁이 남는다 — lenient로 허용.
        lenient().when(provider.getIfAvailable()).thenReturn(aiMixClient);
        return new ItemSemanticSearchService(provider, embeddingRepository, itemRepository,
                new ItemSummaryAssembler(itemCategoryQueryService, s3Uploader), 0.6);
    }

    private Item item(long id) {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.MEMO)
                .title("제목" + id).content("본문").build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    void 거리순으로_아이템을_찾아_목록으로_돌려준다() {
        when(client.embedQuery("일식 코스")).thenReturn(QUERY_EMBEDDING);
        when(embeddingRepository.countSimilar(1L, QUERY_EMBEDDING, 0.6)).thenReturn(2L);
        when(embeddingRepository.searchBySimilarity(1L, QUERY_EMBEDDING, 0.6, 20, 0)).thenReturn(List.of(
                new SimilarityRow(12L, 0.31), new SimilarityRow(10L, 0.44)));
        // findAllById는 순서를 보장하지 않는다 — 일부러 거리 역순으로 돌려줘서 재배열을 검증한다.
        when(itemRepository.findAllById(List.of(12L, 10L))).thenReturn(List.of(item(10L), item(12L)));
        when(itemCategoryQueryService.categoriesByItemIds(any())).thenReturn(Map.of());

        ItemListResponse response = service.search(1L, "일식 코스", PageRequest.of(0, 20));

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content().get(0).itemId()).isEqualTo(12L);
        assertThat(response.content().get(1).itemId()).isEqualTo(10L);
    }

    /** 임계값 안에 아무것도 없으면 null — 무관한 검색어에 "가장 덜 먼" 아이템이 나오면 안 된다. */
    @Test
    void 임계값_안에_결과가_없으면_null이다() {
        when(client.embedQuery("자동차 수리")).thenReturn(QUERY_EMBEDDING);
        when(embeddingRepository.countSimilar(1L, QUERY_EMBEDDING, 0.6)).thenReturn(0L);

        assertThat(service.search(1L, "자동차 수리", PageRequest.of(0, 20))).isNull();
        verifyNoInteractions(itemRepository);
    }

    @Test
    void aimix가_꺼져_있으면_호출_없이_null이다() {
        ItemSemanticSearchService disabled = serviceWith(null);

        assertThat(disabled.search(1L, "일식 코스", PageRequest.of(0, 20))).isNull();
        verifyNoInteractions(embeddingRepository, itemRepository);
    }

    /** 검색은 AI 사이드카 장애로 500이 나면 안 된다 — 실패는 흡수하고 키워드 0건으로 축소된다. */
    @Test
    void 임베딩_호출이_실패하면_예외_없이_null이다() {
        when(client.embedQuery(any())).thenThrow(new IllegalStateException("ai-mix 다운"));

        assertThat(service.search(1L, "일식 코스", PageRequest.of(0, 20))).isNull();
        verifyNoInteractions(embeddingRepository, itemRepository);
    }

    /** 페이지 슬라이스 조회 사이에 삭제된 아이템은 조용히 빠진다 — 응답이 터지면 안 된다. */
    @Test
    void 조회_사이에_사라진_아이템은_건너뛴다() {
        when(client.embedQuery(any())).thenReturn(QUERY_EMBEDDING);
        when(embeddingRepository.countSimilar(anyLong(), any(), anyDouble())).thenReturn(2L);
        when(embeddingRepository.searchBySimilarity(anyLong(), any(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(new SimilarityRow(12L, 0.31), new SimilarityRow(10L, 0.44)));
        when(itemRepository.findAllById(any())).thenReturn(List.of(item(10L)));
        when(itemCategoryQueryService.categoriesByItemIds(any())).thenReturn(Map.of());

        ItemListResponse response = service.search(1L, "일식 코스", PageRequest.of(0, 20));

        assertThat(response.content()).extracting(summary -> summary.itemId()).containsExactly(10L);
    }

    @Test
    void 페이지네이션은_limit과_offset으로_넘긴다() {
        when(client.embedQuery(any())).thenReturn(QUERY_EMBEDDING);
        when(embeddingRepository.countSimilar(anyLong(), any(), anyDouble())).thenReturn(25L);
        // 총 25건에서 세 번째 페이지(offset 20, size 10)면 마지막 5건이 온다.
        List<SimilarityRow> lastPage = List.of(
                new SimilarityRow(30L, 0.50), new SimilarityRow(31L, 0.52),
                new SimilarityRow(32L, 0.54), new SimilarityRow(33L, 0.56),
                new SimilarityRow(34L, 0.58));
        when(embeddingRepository.searchBySimilarity(eq(1L), eq(QUERY_EMBEDDING), eq(0.6), eq(10), eq(20)))
                .thenReturn(lastPage);
        when(itemRepository.findAllById(any()))
                .thenReturn(List.of(item(30L), item(31L), item(32L), item(33L), item(34L)));
        when(itemCategoryQueryService.categoriesByItemIds(any())).thenReturn(Map.of());

        ItemListResponse response = service.search(1L, "일식 코스", PageRequest.of(2, 10));

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(25);
    }
}
