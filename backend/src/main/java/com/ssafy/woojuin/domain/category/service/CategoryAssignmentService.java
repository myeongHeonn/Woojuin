package com.ssafy.woojuin.domain.category.service;

import com.ssafy.woojuin.domain.ai.CategoryCandidate;
import com.ssafy.woojuin.domain.category.CategoryDefaults;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.entity.ItemCategory;
import com.ssafy.woojuin.domain.category.exception.CategoryNotFoundException;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import com.ssafy.woojuin.domain.category.repository.ItemCategoryRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI가 분류한 카테고리 이름들을 아이템에 연결한다. 이름은 그 워크스페이스의 category_id로
 * 매핑하고, 하나도 매칭되지 않으면 "기타"로 폴백해 <b>모든 아이템이 최소 한 카테고리에</b>
 * 속하도록 한다.
 *
 * <p>트랜잭션 경계는 이 메서드를 호출하는 가공 파이프라인이 갖는다(별도 @Transactional을
 * 걸지 않는다) — 미리보기·본문·카테고리 저장이 한 트랜잭션에서 함께 커밋되게 하려는 것.
 */
@Slf4j
@Service
public class CategoryAssignmentService {

    private final CategoryRepository categoryRepository;
    private final ItemCategoryRepository itemCategoryRepository;

    public CategoryAssignmentService(CategoryRepository categoryRepository,
            ItemCategoryRepository itemCategoryRepository) {
        this.categoryRepository = categoryRepository;
        this.itemCategoryRepository = itemCategoryRepository;
    }

    public void assign(Long itemId, Long workspaceId, List<String> categoryNames) {
        List<Category> matched = categoryNames.isEmpty()
                ? List.of()
                : categoryRepository.findByWorkspaceIdAndNameIn(workspaceId, categoryNames);

        if (matched.isEmpty()) {
            // 매칭 실패(AI 미분류/이름 불일치) → "기타"로 폴백. 그것마저 없으면(시드 안 된
            // 옛 워크스페이스) 연결할 카테고리가 없어 그냥 넘어간다.
            matched = categoryRepository.findByWorkspaceIdAndName(workspaceId, CategoryDefaults.ETC)
                    .map(List::of)
                    .orElseGet(List::of);
        }

        for (Category category : matched) {
            if (!itemCategoryRepository.existsByItemIdAndCategoryId(itemId, category.getId())) {
                itemCategoryRepository.save(
                        ItemCategory.builder().itemId(itemId).categoryId(category.getId()).build());
            }
        }
        log.debug("카테고리 연결: itemId={}, categories={}", itemId, matched.stream().map(Category::getName).toList());
    }

    /**
     * 사용자가 고른 카테고리 집합으로 <b>교체</b>한다. AI 경로인 {@link #assign}은 추가
     * 전용이라 해제가 안 되고 이름 기반이어서, 사용자 수정에는 쓸 수 없다.
     *
     * <p>전부 지우고 다시 넣지 않고 지금 연결과 비교해 빠진 것만 지우고 새로 생긴 것만
     * 넣는다 — 안 바뀐 링크의 id가 매번 새로 발급되는 걸 막는다.
     *
     * <p>다른 워크스페이스의 카테고리 id를 섞어 보내면 404다. 검증하지 않으면 남의
     * 워크스페이스 카테고리를 내 아이템에 붙일 수 있다.
     *
     * <p>트랜잭션 경계는 호출자(ItemService.update)가 갖는다 — assign과 같은 규칙.
     */
    public void replace(Long itemId, Long workspaceId, List<Long> categoryIds) {
        Set<Long> requested = new LinkedHashSet<>(categoryIds);   // 같은 id를 여러 번 보내도 한 번만
        verifyAllInWorkspace(requested, workspaceId);

        List<ItemCategory> current = itemCategoryRepository.findByItemId(itemId);
        for (ItemCategory link : current) {
            if (!requested.contains(link.getCategoryId())) {
                itemCategoryRepository.delete(link);
            }
        }

        Set<Long> alreadyLinked = current.stream().map(ItemCategory::getCategoryId).collect(Collectors.toSet());
        for (Long categoryId : requested) {
            if (!alreadyLinked.contains(categoryId)) {
                itemCategoryRepository.save(ItemCategory.builder().itemId(itemId).categoryId(categoryId).build());
            }
        }
        log.debug("카테고리 교체: itemId={}, categoryIds={}", itemId, requested);
    }

    /** 존재하지 않거나 다른 워크스페이스 소속인 id가 하나라도 있으면 통째로 거부한다(부분 반영 금지). */
    private void verifyAllInWorkspace(Set<Long> categoryIds, Long workspaceId) {
        Map<Long, Category> found = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        for (Long categoryId : categoryIds) {
            Category category = found.get(categoryId);
            if (category == null || !category.getWorkspaceId().equals(workspaceId)) {
                throw new CategoryNotFoundException(categoryId);
            }
        }
    }

    /**
     * AI에 넘길 후보 목록(그 워크스페이스의 현재 카테고리). 분류 정확도를 위해 이름만이
     * 아니라 id·판단 기준 설명까지 넘긴다 — 설명이 아직 없는 카테고리(생성 직후 등)는
     * null로 가고, 분석기 쪽에서 이름으로 폴백한다.
     */
    public List<CategoryCandidate> candidates(Long workspaceId) {
        return categoryRepository.findByWorkspaceId(workspaceId).stream()
                .map(category -> new CategoryCandidate(
                        category.getId(), category.getName(), category.getDescription()))
                .toList();
    }
}
