package com.ssafy.woojuin.domain.category.service;

import com.ssafy.woojuin.domain.category.dto.CategoryResponse;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.entity.ItemCategory;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import com.ssafy.woojuin.domain.category.repository.ItemCategoryRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이템에 연결된 카테고리를 읽어 응답에 싣기 위한 조회 전용 서비스. 목록 응답의 N+1을
 * 피하려고 여러 아이템의 카테고리를 두 번의 쿼리(연결 조회 + 카테고리 조회)로 배치 처리한다.
 */
@Service
public class ItemCategoryQueryService {

    private final ItemCategoryRepository itemCategoryRepository;
    private final CategoryRepository categoryRepository;

    public ItemCategoryQueryService(ItemCategoryRepository itemCategoryRepository,
            CategoryRepository categoryRepository) {
        this.itemCategoryRepository = itemCategoryRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<CategoryResponse>> categoriesByItemIds(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        List<ItemCategory> links = itemCategoryRepository.findByItemIdIn(itemIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> categoryIds = links.stream().map(ItemCategory::getCategoryId).collect(Collectors.toSet());
        Map<Long, Category> categoryById = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        Map<Long, List<CategoryResponse>> byItem = new HashMap<>();
        for (ItemCategory link : links) {
            Category category = categoryById.get(link.getCategoryId());
            if (category != null) {   // 조회 사이에 삭제됐으면 건너뛴다
                byItem.computeIfAbsent(link.getItemId(), k -> new ArrayList<>())
                        .add(CategoryResponse.from(category));
            }
        }
        return byItem;
    }

    /**
     * 지도 응답용 경량 변형 (FR-032) — categoryId만 필요할 때 쓴다.
     *
     * <p>{@link #categoriesByItemIds}는 이름·색을 채우려고 categories를 한 번 더 읽지만,
     * 여기는 연결 테이블만 한 번 읽는다(쿼리 2회 → 1회, Category 엔티티 로딩 0). 지도는
     * 핀 색·이름을 카테고리 목록 API에서 이미 받아둔 것으로 칠하므로 id만 있으면 된다.
     *
     * <p>대신 삭제된 카테고리를 가리키는 잔여 연결을 걸러내지 못한다. 카테고리 삭제 시
     * CategoryService.delete가 deleteByCategoryId로 연결을 함께 지우므로 실제로는 그런 행이
     * 남지 않는다 — 이 전제가 깨지면 여기도 categories와 join하는 방식으로 바꿔야 한다.
     *
     * <p>id를 정렬해서 반환한다. findByItemIdIn이 순서를 보장하지 않아, 정렬하지 않으면
     * 같은 요청에 응답 배열 순서가 달라질 수 있다.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> categoryIdsByItemIds(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> byItem = new HashMap<>();
        for (ItemCategory link : itemCategoryRepository.findByItemIdIn(itemIds)) {
            byItem.computeIfAbsent(link.getItemId(), k -> new ArrayList<>()).add(link.getCategoryId());
        }
        byItem.values().forEach(ids -> ids.sort(null));
        return byItem;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categoriesOf(Long itemId) {
        return categoriesByItemIds(List.of(itemId)).getOrDefault(itemId, List.of());
    }

    /** 카테고리 필터용 — 그 카테고리들 중 하나라도(OR) 연결된 아이템 id 목록(없으면 빈 리스트). */
    @Transactional(readOnly = true)
    public List<Long> itemIdsInCategories(Collection<Long> categoryIds) {
        return itemCategoryRepository.findItemIdsByCategoryIdIn(categoryIds);
    }
}
