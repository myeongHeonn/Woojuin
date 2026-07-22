package com.ssafy.woojuin.domain.category.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.category.CategoryDefaults;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import com.ssafy.woojuin.domain.workspace.event.WorkspaceCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategorySeederTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategorySeeder seeder;

    @Test
    void 워크스페이스_생성시_기본_카테고리를_시드한다() {
        when(categoryRepository.existsByWorkspaceId(1L)).thenReturn(false);

        seeder.seedDefaults(new WorkspaceCreatedEvent(1L));

        verify(categoryRepository, times(CategoryDefaults.NAMES.size())).save(org.mockito.ArgumentMatchers.any(Category.class));
    }

    @Test
    void 이미_카테고리가_있으면_시드하지_않는다() {
        when(categoryRepository.existsByWorkspaceId(1L)).thenReturn(true);

        seeder.seedDefaults(new WorkspaceCreatedEvent(1L));

        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
