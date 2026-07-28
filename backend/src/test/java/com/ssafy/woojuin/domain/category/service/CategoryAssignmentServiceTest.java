package com.ssafy.woojuin.domain.category.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.category.CategoryDefaults;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.entity.ItemCategory;
import com.ssafy.woojuin.domain.category.exception.CategoryNotFoundException;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import com.ssafy.woojuin.domain.category.repository.ItemCategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryAssignmentServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock ItemCategoryRepository itemCategoryRepository;
    @InjectMocks CategoryAssignmentService service;

    private Category category(long id, String name) {
        Category c = Category.builder().workspaceId(1L).name(name).build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    @Test
    void 매칭된_카테고리를_아이템에_연결한다() {
        Category learn = category(10L, "학습·지식");
        when(categoryRepository.findByWorkspaceIdAndNameIn(1L, List.of("학습·지식")))
                .thenReturn(List.of(learn));
        when(itemCategoryRepository.existsByItemIdAndCategoryId(5L, 10L)).thenReturn(false);

        service.assign(5L, 1L, List.of("학습·지식"));

        verify(itemCategoryRepository).save(any(ItemCategory.class));
    }

    @Test
    void 매칭이_없으면_기타로_폴백한다() {
        Category etc = category(99L, CategoryDefaults.ETC);
        when(categoryRepository.findByWorkspaceIdAndNameIn(anyLong(), any()))
                .thenReturn(List.of());
        when(categoryRepository.findByWorkspaceIdAndName(1L, CategoryDefaults.ETC))
                .thenReturn(Optional.of(etc));
        when(itemCategoryRepository.existsByItemIdAndCategoryId(5L, 99L)).thenReturn(false);

        service.assign(5L, 1L, List.of("없는카테고리"));

        verify(itemCategoryRepository).save(any(ItemCategory.class));
    }

    @Test
    void AI가_빈_결과여도_기타로_폴백한다() {
        Category etc = category(99L, CategoryDefaults.ETC);
        when(categoryRepository.findByWorkspaceIdAndName(1L, CategoryDefaults.ETC))
                .thenReturn(Optional.of(etc));
        when(itemCategoryRepository.existsByItemIdAndCategoryId(5L, 99L)).thenReturn(false);

        service.assign(5L, 1L, List.of());   // AI 미분류

        verify(itemCategoryRepository).save(any(ItemCategory.class));
        // 이름 목록이 비면 조회 자체를 생략한다
        verify(categoryRepository, never()).findByWorkspaceIdAndNameIn(anyLong(), any());
    }

    @Test
    void 기타조차_없으면_아무것도_연결하지_않는다() {
        when(categoryRepository.findByWorkspaceIdAndName(1L, CategoryDefaults.ETC))
                .thenReturn(Optional.empty());

        service.assign(5L, 1L, List.of());   // 시드 안 된 옛 워크스페이스

        verify(itemCategoryRepository, never()).save(any());
    }

    @Test
    void 이미_연결되어_있으면_중복_저장하지_않는다() {
        Category learn = category(10L, "학습·지식");
        when(categoryRepository.findByWorkspaceIdAndNameIn(1L, List.of("학습·지식")))
                .thenReturn(List.of(learn));
        when(itemCategoryRepository.existsByItemIdAndCategoryId(5L, 10L)).thenReturn(true);

        service.assign(5L, 1L, List.of("학습·지식"));

        verify(itemCategoryRepository, never()).save(any());
    }

    private ItemCategory link(long id, long itemId, long categoryId) {
        ItemCategory ic = ItemCategory.builder().itemId(itemId).categoryId(categoryId).build();
        ReflectionTestUtils.setField(ic, "id", id);
        return ic;
    }

    @Test
    void 교체는_빠진_것만_지우고_새것만_넣는다() {
        // 10은 유지, 11은 해제, 12는 신규 — 유지되는 10은 지우지도 다시 넣지도 않아야 한다.
        ItemCategory keep = link(1L, 5L, 10L);
        ItemCategory drop = link(2L, 5L, 11L);
        when(categoryRepository.findAllById(any()))
                .thenReturn(List.of(category(10L, "학습·지식"), category(12L, "쇼핑")));
        when(itemCategoryRepository.findByItemId(5L)).thenReturn(List.of(keep, drop));

        service.replace(5L, 1L, List.of(10L, 12L));

        verify(itemCategoryRepository).delete(drop);
        verify(itemCategoryRepository, never()).delete(keep);
        verify(itemCategoryRepository, times(1)).save(any(ItemCategory.class));
    }

    @Test
    void 교체는_기존_연결을_전부_해제할_수도_있다() {
        // 새 집합에 없는 기존 연결은 모두 지워진다(교체이지 추가가 아니다).
        ItemCategory old1 = link(1L, 5L, 10L);
        ItemCategory old2 = link(2L, 5L, 11L);
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category(12L, "쇼핑")));
        when(itemCategoryRepository.findByItemId(5L)).thenReturn(List.of(old1, old2));

        service.replace(5L, 1L, List.of(12L));

        verify(itemCategoryRepository).delete(old1);
        verify(itemCategoryRepository).delete(old2);
    }

    @Test
    void 같은_id를_여러번_보내도_한번만_연결한다() {
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category(10L, "학습·지식")));
        when(itemCategoryRepository.findByItemId(5L)).thenReturn(List.of());

        service.replace(5L, 1L, List.of(10L, 10L, 10L));

        verify(itemCategoryRepository, times(1)).save(any(ItemCategory.class));
    }

    @Test
    void 없는_카테고리_id가_있으면_404이고_아무것도_바꾸지_않는다() {
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category(10L, "학습·지식")));

        assertThatThrownBy(() -> service.replace(5L, 1L, List.of(10L, 999L)))
                .isInstanceOf(CategoryNotFoundException.class);

        // 부분 반영 금지 — 검증이 끝나기 전엔 기존 연결을 읽지도 않는다.
        verify(itemCategoryRepository, never()).findByItemId(anyLong());
        verify(itemCategoryRepository, never()).save(any());
        verify(itemCategoryRepository, never()).delete(any());
    }

    @Test
    void 다른_워크스페이스_카테고리는_붙일_수_없다() {
        // 남의 워크스페이스 카테고리 id를 알아내 내 아이템에 붙이는 걸 막는다.
        Category otherWorkspace = Category.builder().workspaceId(2L).name("남의 카테고리").build();
        ReflectionTestUtils.setField(otherWorkspace, "id", 77L);
        when(categoryRepository.findAllById(any())).thenReturn(List.of(otherWorkspace));

        assertThatThrownBy(() -> service.replace(5L, 1L, List.of(77L)))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(itemCategoryRepository, never()).save(any());
    }
}
