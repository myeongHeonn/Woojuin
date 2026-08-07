package com.ssafy.woojuin.domain.item.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.ai.AiMixClient;
import com.ssafy.woojuin.domain.ai.AiMixClient.EmbeddingResult;
import com.ssafy.woojuin.domain.ai.AiMixClient.ItemPoint;
import com.ssafy.woojuin.domain.ai.AiMixClient.ItemVector;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.entity.ItemCategory;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import com.ssafy.woojuin.domain.category.repository.ItemCategoryRepository;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.global.sse.WorkspaceChangedEvent;
import com.ssafy.woojuin.global.sse.WorkspaceEventType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemEmbeddingServiceTest {

    @Mock AiMixClient client;
    @Mock ItemRepository itemRepository;
    @Mock ItemCategoryRepository itemCategoryRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ItemEmbeddingJdbcRepository embeddingRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private ItemEmbeddingService service;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<AiMixClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        service = new ItemEmbeddingService(provider, itemRepository, itemCategoryRepository,
                categoryRepository, embeddingRepository, eventPublisher);
    }

    private Item item(long id, String summary) {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.MEMO)
                .title("제목").content("본문").build();
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "summary", summary);
        return item;
    }

    private void givenCategories(long itemId) {
        ItemCategory link = ItemCategory.builder().itemId(itemId).categoryId(7L).build();
        Category category = Category.builder().workspaceId(1L).name("생활·할 일").build();
        ReflectionTestUtils.setField(category, "id", 7L);
        when(itemCategoryRepository.findByItemId(itemId)).thenReturn(List.of(link));
        when(categoryRepository.findAllById(List.of(7L))).thenReturn(List.of(category));
    }

    @Test
    void 임베딩을_저장하고_워크스페이스_좌표를_재계산한다() {
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item(10L, "요약")));
        givenCategories(10L);
        when(client.createEmbedding(anyLong(), anyString(), anyString(), anyList()))
                .thenReturn(new EmbeddingResult("model", "sha256:abc", new float[] {0.1f}));
        when(embeddingRepository.findInputHash(10L)).thenReturn(Optional.empty());
        when(embeddingRepository.findActiveVectors(1L))
                .thenReturn(List.of(new ItemVector(10L, new float[] {0.1f})));
        when(client.reduceCoordinates(anyList()))
                .thenReturn(List.of(new ItemPoint(10L, 1.0, 2.0, 3.0)));

        service.onItemProcessed(10L);

        verify(embeddingRepository).upsert(anyLong(), anyLong(), any(), anyString(), anyString());
        verify(embeddingRepository).updateCoordinates(anyList());
        // 좌표가 준비된 시점에 ITEM 신호를 한 번 더 쏴야 프론트가 새 별을 받는다(SSE 타이밍 버그 수정)
        verify(eventPublisher).publishEvent(WorkspaceChangedEvent.of(1L, WorkspaceEventType.ITEM));
    }

    /** at-least-once 재배달·무변경 재처리 시 임베딩·UMAP을 다시 돌리지 않는다. */
    @Test
    void 입력_해시가_같으면_저장과_재계산을_건너뛴다() {
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item(10L, "요약")));
        givenCategories(10L);
        when(client.createEmbedding(anyLong(), anyString(), anyString(), anyList()))
                .thenReturn(new EmbeddingResult("model", "sha256:same", new float[] {0.1f}));
        when(embeddingRepository.findInputHash(10L)).thenReturn(Optional.of("sha256:same"));

        service.onItemProcessed(10L);

        verify(embeddingRepository, never()).upsert(anyLong(), anyLong(), any(), anyString(), anyString());
        verify(client, never()).reduceCoordinates(anyList());
        verify(eventPublisher, never()).publishEvent(any());
    }

    /** 임베딩 입력 계약(제목+요약)상 요약이 없으면 보낼 것이 없다. */
    @Test
    void 요약이_없으면_임베딩하지_않는다() {
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item(10L, null)));

        service.onItemProcessed(10L);

        verify(client, never()).createEmbedding(anyLong(), anyString(), anyString(), anyList());
    }

    /**
     * 본문·미리보기 설명이 모두 비면 요약은 제목만 보고 지어낸 문장이다 — 그 임베딩은
     * 아무 검색어에나 걸리는 오탐원이라 만들지 않는다(크롤링 실패 URL 아이템 실측).
     */
    @Test
    void 본문과_미리보기가_모두_비면_임베딩하지_않는다() {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.URL)
                .title("smartstore.naver.com/whatever").build();
        ReflectionTestUtils.setField(item, "id", 10L);
        ReflectionTestUtils.setField(item, "summary", "제공된 본문과 설명이 없어 추가 정보가 없습니다.");
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        service.onItemProcessed(10L);

        verify(client, never()).createEmbedding(anyLong(), anyString(), anyString(), anyList());
    }

    /** 본문이 없어도 미리보기 설명이 있으면 임베딩 신호로 충분하다(지도 공유 링크류). */
    @Test
    void 미리보기_설명만_있어도_임베딩한다() {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.URL)
                .title("투썸플레이스").build();
        ReflectionTestUtils.setField(item, "id", 10L);
        ReflectionTestUtils.setField(item, "summary", "광주 하남3지구의 투썸플레이스 지점.");
        ReflectionTestUtils.setField(item, "previewDescription", "카페 · 광주 광산구");
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        givenCategories(10L);
        when(client.createEmbedding(anyLong(), anyString(), anyString(), anyList()))
                .thenReturn(new EmbeddingResult("model", "sha256:abc", new float[] {0.1f}));
        when(embeddingRepository.findInputHash(10L)).thenReturn(Optional.empty());
        when(embeddingRepository.findActiveVectors(1L))
                .thenReturn(List.of(new ItemVector(10L, new float[] {0.1f})));
        when(client.reduceCoordinates(anyList()))
                .thenReturn(List.of(new ItemPoint(10L, 1.0, 2.0, 3.0)));

        service.onItemProcessed(10L);

        verify(embeddingRepository).upsert(anyLong(), anyLong(), any(), anyString(), anyString());
    }

    /** 임베딩·좌표는 부가 정보 — 실패가 가공 완료(ACK)를 뒤집으면 안 된다. */
    @Test
    void 어떤_실패도_밖으로_새지_않는다() {
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item(10L, "요약")));
        givenCategories(10L);
        when(client.createEmbedding(anyLong(), anyString(), anyString(), anyList()))
                .thenThrow(new IllegalStateException("aimix down"));

        assertThatCode(() -> service.onItemProcessed(10L)).doesNotThrowAnyException();
    }
}
