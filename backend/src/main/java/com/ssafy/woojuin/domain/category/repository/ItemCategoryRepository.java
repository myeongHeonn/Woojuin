package com.ssafy.woojuin.domain.category.repository;

import com.ssafy.woojuin.domain.category.entity.ItemCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, Long> {

    List<ItemCategory> findByItemId(Long itemId);

    boolean existsByItemIdAndCategoryId(Long itemId, Long categoryId);

    /** 카테고리 삭제 시 연결 정리용. */
    void deleteByCategoryId(Long categoryId);

    void deleteByItemId(Long itemId);
}
