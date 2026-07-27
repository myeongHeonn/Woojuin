package com.ssafy.woojuin.domain.category.repository;

import com.ssafy.woojuin.domain.category.entity.ItemCategory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, Long> {

    List<ItemCategory> findByItemId(Long itemId);

    /** 목록 응답에서 여러 아이템의 카테고리를 한 번에 읽어 N+1을 피한다. */
    List<ItemCategory> findByItemIdIn(Collection<Long> itemIds);

    /** 카테고리 필터용 — 그 카테고리에 연결된 아이템 id들. */
    @Query("select ic.itemId from ItemCategory ic where ic.categoryId = :categoryId")
    List<Long> findItemIdsByCategoryId(Long categoryId);

    boolean existsByItemIdAndCategoryId(Long itemId, Long categoryId);

    /** 카테고리 삭제 시 연결 정리용. */
    void deleteByCategoryId(Long categoryId);

    void deleteByItemId(Long itemId);
}
