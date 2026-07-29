package com.ssafy.woojuin.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.category.CategoryDefaults;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryDescriptionBackfillTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategoryDescriptionBackfill backfill;

    private Category category(String name) {
        return Category.builder().workspaceId(1L).name(name).build();
    }

    @Test
    void 기본_카테고리_이름이면_표준_설명을_채운다() {
        Category etc = category(CategoryDefaults.ETC);
        Category custom = category("나만의 모음");
        when(categoryRepository.findByDescriptionIsNull()).thenReturn(List.of(etc, custom));

        backfill.backfill();

        assertThat(etc.getDescription()).isEqualTo(CategoryDefaults.DESCRIPTIONS.get(CategoryDefaults.ETC));
        // 사용자 카테고리는 정해진 설명이 없으니 그대로 둔다(분류 시 이름 폴백).
        assertThat(custom.getDescription()).isNull();
    }

    @Test
    void 이름을_바꾸면_설명이_비워진다() {
        Category category = Category.builder()
                .workspaceId(1L).name("옛이름").description("옛이름 기준 설명").build();

        category.rename("새이름");

        assertThat(category.getDescription()).isNull();
    }
}
