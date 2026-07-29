package com.ssafy.woojuin.domain.category.service;

import com.ssafy.woojuin.domain.category.CategoryDefaults;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * description 컬럼 도입 이전에 만들어진 카테고리(description=null) 중 <b>기본 카테고리와
 * 같은 이름</b>인 것을 기동 시 한 번 채운다 (CategoryColorBackfill과 같은 자가치유 패턴 —
 * ddl-auto=update만 쓰고 마이그레이션 도구가 없어서다).
 *
 * <p>사용자가 만든 카테고리는 여기서 채우지 않는다 — 정해진 설명이 없고, LLM 생성은
 * 기동 경로에 넣기엔 느리고 실패 가능해서다. 그런 카테고리는 분류 시 이름으로 폴백하고,
 * 새로 만들어지는 것부터는 {@code CategoryDescriptionGenerator}가 비동기로 채운다.
 */
@Slf4j
@Component
public class CategoryDescriptionBackfill {

    private final CategoryRepository categoryRepository;

    public CategoryDescriptionBackfill(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        List<Category> missing = categoryRepository.findByDescriptionIsNull();
        int fixed = 0;
        for (Category category : missing) {
            String description = CategoryDefaults.DESCRIPTIONS.get(category.getName());
            if (description != null) {
                category.applyDescription(description);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("카테고리 설명 백필 완료: {}건", fixed);
        }
    }
}
