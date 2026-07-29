package com.ssafy.woojuin.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.category.CategoryDefaults;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService;
import com.ssafy.woojuin.domain.item.dto.UniverseResponse;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository.CoordinateRow;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UniverseServiceTest {

    @Mock ItemEmbeddingJdbcRepository embeddingRepository;
    @Mock ItemCategoryQueryService itemCategoryQueryService;
    @Mock CategoryRepository categoryRepository;
    @Mock WorkspaceMemberRepository workspaceMemberRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @InjectMocks UniverseService service;

    private Category category(long id, String name, String color) {
        Category c = Category.builder().workspaceId(1L).name(name).color(color).build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private CoordinateRow row(long itemId, String type, String url) {
        return new CoordinateRow(itemId, type, "제목" + itemId, url, 1.0, 2.0, 3.0);
    }

    @BeforeEach
    void setUp() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
    }

    @Test
    void 카테고리별로_묶고_기타_전용은_unclassified로_분리한다() {
        when(embeddingRepository.findCoordinates(1L)).thenReturn(List.of(
                row(10L, "MEMO", null), row(11L, "URL", "https://example.com")));
        when(itemCategoryQueryService.categoryIdsByItemIds(anyCollection())).thenReturn(Map.of(
                10L, List.of(2L),          // 학습·지식
                11L, List.of(9L)));        // 기타만 → unclassified
        when(categoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(
                category(2L, "학습·지식", "#A8D8B9"),
                category(9L, CategoryDefaults.ETC, "#F5F1E8")));

        UniverseResponse universe = service.universe(1L, 1L);

        assertThat(universe.constellations()).hasSize(1);
        assertThat(universe.constellations().get(0).categoryName()).isEqualTo("학습·지식");
        assertThat(universe.constellations().get(0).color()).isEqualTo("#A8D8B9");
        assertThat(universe.constellations().get(0).items()).hasSize(1);
        assertThat(universe.constellations().get(0).items().get(0).position())
                .containsExactly(1.0, 2.0, 3.0);
        assertThat(universe.unclassified()).hasSize(1);
        assertThat(universe.unclassified().get(0).url()).isEqualTo("https://example.com");
    }

    /** 프론트 목업 계약: 여러 카테고리에 속한 아이템은 각 별자리에 중복으로 들어간다. */
    @Test
    void 다중_카테고리_아이템은_각_별자리에_중복으로_들어간다() {
        when(embeddingRepository.findCoordinates(1L)).thenReturn(List.of(row(10L, "MEMO", null)));
        when(itemCategoryQueryService.categoryIdsByItemIds(anyCollection()))
                .thenReturn(Map.of(10L, List.of(2L, 5L)));
        when(categoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(
                category(2L, "학습·지식", "#A8D8B9"),
                category(5L, "음식·맛집", "#F2D96B")));

        UniverseResponse universe = service.universe(1L, 1L);

        assertThat(universe.constellations()).hasSize(2);
        assertThat(universe.constellations()).allSatisfy(constellation ->
                assertThat(constellation.items()).extracting(s -> s.id()).containsExactly(10L));
        assertThat(universe.unclassified()).isEmpty();
    }

    /** 기타 + 실제 카테고리에 함께 속하면 실제 별자리에만 나온다(기타는 폴백일 뿐). */
    @Test
    void 기타와_실제_카테고리에_함께_속하면_별자리에만_나온다() {
        when(embeddingRepository.findCoordinates(1L)).thenReturn(List.of(row(10L, "MEMO", null)));
        when(itemCategoryQueryService.categoryIdsByItemIds(anyCollection()))
                .thenReturn(Map.of(10L, List.of(2L, 9L)));
        when(categoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(
                category(2L, "학습·지식", "#A8D8B9"),
                category(9L, CategoryDefaults.ETC, "#F5F1E8")));

        UniverseResponse universe = service.universe(1L, 1L);

        assertThat(universe.constellations()).hasSize(1);
        assertThat(universe.unclassified()).isEmpty();
    }

    @Test
    void 임베딩이_없으면_빈_우주다() {
        when(embeddingRepository.findCoordinates(1L)).thenReturn(List.of());

        UniverseResponse universe = service.universe(1L, 1L);

        assertThat(universe.constellations()).isEmpty();
        assertThat(universe.unclassified()).isEmpty();
    }
}
