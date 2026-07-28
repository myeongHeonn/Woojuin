package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.category.dto.CategoryResponse;
import com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService;
import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSummaryResponse;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Item 페이지를 목록 응답으로 변환하는 공통 조립기. 목록·휴지통({@link ItemService})과
 * 검색({@link ItemSearchService})이 같은 카드 형태를 내려줘야 해서 한곳에 모았다 —
 * 카테고리 배치 조회(N+1 방지)와 IMAGE presigned URL 폴백 규칙이 갈라지면 안 된다.
 */
@Component
public class ItemSummaryAssembler {

    private final ItemCategoryQueryService itemCategoryQueryService;
    private final S3Uploader s3Uploader;

    public ItemSummaryAssembler(ItemCategoryQueryService itemCategoryQueryService, S3Uploader s3Uploader) {
        this.itemCategoryQueryService = itemCategoryQueryService;
        this.s3Uploader = s3Uploader;
    }

    /** 페이지의 아이템들에 카테고리를 배치로 채워 목록 응답으로 변환한다(N+1 방지). */
    public ItemListResponse toListResponse(Page<Item> items) {
        Map<Long, List<CategoryResponse>> categoriesByItem = itemCategoryQueryService.categoriesByItemIds(
                items.getContent().stream().map(Item::getId).toList());
        Page<ItemSummaryResponse> mapped = items.map(item ->
                ItemSummaryResponse.from(item, categoriesByItem.getOrDefault(item.getId(), List.of()),
                        thumbnailImageUrlOf(item)));
        return ItemListResponse.from(mapped);
    }

    /**
     * 목록용 IMAGE presigned URL — 저용량 썸네일을 우선 쓰고, 아직 생성 전(PROCESSING)이거나
     * 생성이 실패해 없으면 원본으로 폴백한다. 목록 카드는 이미지를 작게 보여주므로 썸네일이면
     * 충분하고 로딩도 빠르다.
     */
    private String thumbnailImageUrlOf(Item item) {
        if (item.getType() != ItemType.IMAGE) {
            return null;
        }
        String key = item.getThumbnailS3Key() != null ? item.getThumbnailS3Key() : item.getS3Key();
        return key != null ? s3Uploader.presignGet(key) : null;
    }
}
