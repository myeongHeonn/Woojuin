package com.ssafy.woojuin.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.item.dto.ItemSearchResponse;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.repository.ItemSearchRepository;
import com.ssafy.woojuin.domain.item.repository.MatchMode;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemSearchServiceTest {

    @Mock
    private ItemSearchRepository itemSearchRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService itemCategoryQueryService;

    @Mock
    private S3Uploader s3Uploader;

    @Mock
    private ItemSemanticSearchService itemSemanticSearchService;

    private ItemSearchService itemSearchService;

    @BeforeEach
    void setUp() {
        itemSearchService = new ItemSearchService(itemSearchRepository, workspaceMemberRepository,
                new ItemSummaryAssembler(itemCategoryQueryService, s3Uploader), itemSemanticSearchService);

        lenient().when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), any()))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        lenient().when(itemCategoryQueryService.categoriesByItemIds(any())).thenReturn(java.util.Map.of());
    }

    private Item itemOf(long id) {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.MEMO).content("메모").build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Page<Item> pageOf(Long... ids) {
        List<Item> items = java.util.Arrays.stream(ids).map(this::itemOf).toList();
        return new PageImpl<>(items, PageRequest.of(0, 20), items.size());
    }

    private Page<Item> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
    }

    @Test
    void 멤버가_아니면_검색할_수_없다() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemSearchService.search(1L, 99L, "파스타", 0, 20))
                .isInstanceOf(WorkspaceAccessDeniedException.class);

        verifyNoInteractions(itemSearchRepository);
    }

    @Test
    void 검색어가_있으면_결과를_목록_형태로_돌려준다() {
        when(itemSearchRepository.search(eq(1L), anyList(), eq(MatchMode.ALL), any(Pageable.class)))
                .thenReturn(pageOf(7L));

        ItemSearchResponse response = itemSearchService.search(1L, 1L, "파스타", 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().get(0).itemId()).isEqualTo(7L);
        assertThat(response.partialMatch()).isFalse();
    }

    /** 패턴이 '%%'가 되면 워크스페이스 전체를 긁어오므로 쿼리 자체를 태우면 안 된다. */
    @Test
    void 검색어가_비면_쿼리없이_빈_결과() {
        ItemSearchResponse response = itemSearchService.search(1L, 1L, "   ", 0, 20);

        assertThat(response.totalElements()).isZero();
        assertThat(response.content()).isEmpty();
        verify(itemSearchRepository, never()).search(any(), anyList(), any(), any());
    }

    @Test
    void 검색어가_null이어도_빈_결과() {
        ItemSearchResponse response = itemSearchService.search(1L, 1L, null, 0, 20);

        assertThat(response.content()).isEmpty();
        verify(itemSearchRepository, never()).search(any(), anyList(), any(), any());
    }

    @Test
    void 여러_단어는_토큰으로_쪼개서_넘긴다() {
        when(itemSearchRepository.search(any(), anyList(), any(), any(Pageable.class))).thenReturn(pageOf(7L));

        itemSearchService.search(1L, 1L, "  파스타   을지로 ", 0, 20);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemSearchRepository).search(eq(1L), captor.capture(), eq(MatchMode.ALL), any(Pageable.class));
        assertThat(captor.getValue()).containsExactly("%파스타%", "%을지로%");
    }

    /** 모든 단어를 포함한 결과가 없으면 일부만 포함한 결과로 폴백하고, 그 사실을 응답에 싣는다. */
    @Test
    void ALL이_0건이면_ANY로_폴백하고_partialMatch를_표시한다() {
        when(itemSearchRepository.search(any(), anyList(), eq(MatchMode.ALL), any(Pageable.class)))
                .thenReturn(emptyPage());
        when(itemSearchRepository.search(any(), anyList(), eq(MatchMode.ANY), any(Pageable.class)))
                .thenReturn(pageOf(9L));

        ItemSearchResponse response = itemSearchService.search(1L, 1L, "파스타 을지로", 0, 20);

        assertThat(response.partialMatch()).isTrue();
        assertThat(response.content().get(0).itemId()).isEqualTo(9L);
    }

    /** 단어가 하나면 ALL과 ANY가 같은 쿼리라 두 번 도는 건 낭비다. */
    @Test
    void 단어가_하나면_ANY_폴백을_하지_않는다() {
        when(itemSearchRepository.search(any(), anyList(), eq(MatchMode.ALL), any(Pageable.class)))
                .thenReturn(emptyPage());

        ItemSearchResponse response = itemSearchService.search(1L, 1L, "파스타", 0, 20);

        assertThat(response.totalElements()).isZero();
        assertThat(response.partialMatch()).isFalse();
        verify(itemSearchRepository, never()).search(any(), anyList(), eq(MatchMode.ANY), any(Pageable.class));
    }

    @Test
    void ANY_폴백도_0건이면_partialMatch는_false() {
        when(itemSearchRepository.search(any(), anyList(), any(), any(Pageable.class))).thenReturn(emptyPage());

        ItemSearchResponse response = itemSearchService.search(1L, 1L, "파스타 을지로", 0, 20);

        assertThat(response.totalElements()).isZero();
        assertThat(response.partialMatch()).isFalse();
    }

    /** 키워드만으로 페이지가 넘치면 보충하지 않는다 — 임베딩 HTTP 호출을 아낀다. */
    @Test
    void 키워드_결과가_페이지를_넘치면_의미_보충을_타지_않는다() {
        List<Item> fullPage = java.util.stream.LongStream.rangeClosed(1, 20).mapToObj(this::itemOf).toList();
        when(itemSearchRepository.search(eq(1L), anyList(), eq(MatchMode.ALL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(fullPage, PageRequest.of(0, 20), 21));

        itemSearchService.search(1L, 1L, "파스타", 0, 20);

        verifyNoInteractions(itemSemanticSearchService);
    }

    /** "카페" 사례 — 키워드 1건이 의미상 관련한 아이템들을 가리면 안 된다. 보충이 뒤에 붙는다. */
    @Test
    void 키워드_결과가_페이지를_못_채우면_의미_보충을_뒤에_붙인다() {
        when(itemSearchRepository.search(eq(1L), anyList(), eq(MatchMode.ALL), any(Pageable.class)))
                .thenReturn(pageOf(7L));
        var supplementItem = mock(com.ssafy.woojuin.domain.item.dto.ItemSummaryResponse.class);
        when(itemSemanticSearchService.supplement(eq(1L), eq("카페"), eq(List.of(7L)), eq(19), eq(0)))
                .thenReturn(new ItemSemanticSearchService.Supplement(List.of(supplementItem), 3));

        ItemSearchResponse response = itemSearchService.search(1L, 1L, "카페", 0, 20);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).itemId()).isEqualTo(7L);   // 정확 일치가 항상 먼저
        assertThat(response.totalElements()).isEqualTo(4);              // 키워드 1 + 보충 3
        assertThat(response.semanticSupplementCount()).isEqualTo(1);    // 이 페이지의 보충 개수
        assertThat(response.semanticMatch()).isFalse();                 // 폴백이 아니라 보충이다
    }

    /** 보충이 불가능(aimix 꺼짐/실패)하면 키워드 결과만 그대로 — 검색이 사이드카에 볼모잡히면 안 된다. */
    @Test
    void 보충이_null이면_키워드_결과만_응답한다() {
        when(itemSearchRepository.search(eq(1L), anyList(), eq(MatchMode.ALL), any(Pageable.class)))
                .thenReturn(pageOf(7L));
        when(itemSemanticSearchService.supplement(any(), any(), anyList(), anyInt(), anyInt()))
                .thenReturn(null);

        ItemSearchResponse response = itemSearchService.search(1L, 1L, "카페", 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.semanticSupplementCount()).isZero();
    }

    /** 키워드가 끝난 뒷 페이지는 전부 보충 — 키워드 id를 다시 읽어 중복을 거르고 오프셋을 잇는다. */
    @Test
    void 뒷_페이지는_키워드_id를_다시_읽어_보충을_이어_붙인다() {
        // 1페이지 요청: 키워드 총 2건이라 이 페이지의 키워드 슬라이스는 비어 있다.
        when(itemSearchRepository.search(eq(1L), anyList(), eq(MatchMode.ALL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 20), 2))   // 요청 페이지
                .thenReturn(pageOf(7L, 8L));                                       // 중복 제거용 0페이지 재조회
        var supplementItem = mock(com.ssafy.woojuin.domain.item.dto.ItemSummaryResponse.class);
        when(itemSemanticSearchService.supplement(eq(1L), eq("카페"), eq(List.of(7L, 8L)), eq(20), eq(18)))
                .thenReturn(new ItemSemanticSearchService.Supplement(List.of(supplementItem), 30));

        ItemSearchResponse response = itemSearchService.search(1L, 1L, "카페", 1, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(32);   // 키워드 2 + 보충 30
        assertThat(response.semanticSupplementCount()).isEqualTo(1);
    }

    @Test
    void 키워드가_전부_0건이면_의미_검색으로_폴백하고_semanticMatch를_표시한다() {
        when(itemSearchRepository.search(any(), anyList(), any(), any(Pageable.class))).thenReturn(emptyPage());
        when(itemSemanticSearchService.search(eq(1L), eq("일식 코스"), any(Pageable.class)))
                .thenReturn(new com.ssafy.woojuin.domain.item.dto.ItemListResponse(
                        List.of(mock(com.ssafy.woojuin.domain.item.dto.ItemSummaryResponse.class)), 0, 20, 1));

        ItemSearchResponse response = itemSearchService.search(1L, 1L, " 일식 코스 ", 0, 20);

        assertThat(response.semanticMatch()).isTrue();
        assertThat(response.partialMatch()).isFalse();
        assertThat(response.totalElements()).isEqualTo(1);
    }

    /** 의미 검색이 불가능(aimix 꺼짐/실패)하거나 임계값 안에 없으면 null — 그냥 0건 응답이다. */
    @Test
    void 의미_검색이_null이면_0건_그대로_응답한다() {
        when(itemSearchRepository.search(any(), anyList(), any(), any(Pageable.class))).thenReturn(emptyPage());
        when(itemSemanticSearchService.search(any(), any(), any())).thenReturn(null);

        ItemSearchResponse response = itemSearchService.search(1L, 1L, "일식 코스", 0, 20);

        assertThat(response.totalElements()).isZero();
        assertThat(response.semanticMatch()).isFalse();
        assertThat(response.partialMatch()).isFalse();
    }

    /** 빈 검색어는 임베딩할 의미가 없다 — 키워드처럼 의미 검색도 건너뛴다. */
    @Test
    void 검색어가_비면_의미_검색도_타지_않는다() {
        itemSearchService.search(1L, 1L, "   ", 0, 20);

        verifyNoInteractions(itemSemanticSearchService);
    }

    /** %를 그대로 넘기면 와일드카드가 되어 워크스페이스 전체가 매칭된다. */
    @Test
    void LIKE_메타문자는_이스케이프된다() {
        assertThat(ItemSearchService.toLikePattern("100%")).isEqualTo("%100\\%%");
        assertThat(ItemSearchService.toLikePattern("a_b")).isEqualTo("%a\\_b%");
        assertThat(ItemSearchService.toLikePattern("c\\d")).isEqualTo("%c\\\\d%");
    }

    @Test
    void 토큰_개수는_상한을_넘지_않는다() {
        assertThat(ItemSearchService.toLikePatterns("가 나 다 라 마 바 사")).hasSize(5);
    }

    @Test
    void 지나치게_긴_검색어는_잘라서_쓴다() {
        List<String> patterns = ItemSearchService.toLikePatterns("가".repeat(500));

        assertThat(patterns).containsExactly("%" + "가".repeat(200) + "%");
    }

    @Test
    void size가_상한을_넘으면_잘린다() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(itemSearchRepository.search(any(), anyList(), any(), any(Pageable.class))).thenReturn(emptyPage());

        itemSearchService.search(1L, 1L, "파스타", 0, 100_000);

        verify(itemSearchRepository).search(any(), anyList(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    /** 정렬은 리포지토리의 ORDER BY가 담당한다. Pageable에 Sort가 실리면 그걸 밀어낸다. */
    @Test
    void 페이저블에_정렬을_싣지_않는다() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(itemSearchRepository.search(any(), anyList(), any(), any(Pageable.class))).thenReturn(emptyPage());

        itemSearchService.search(1L, 1L, "파스타", 0, 20);

        verify(itemSearchRepository).search(any(), anyList(), any(), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }
}
