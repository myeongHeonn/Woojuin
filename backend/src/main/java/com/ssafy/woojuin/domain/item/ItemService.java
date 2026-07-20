package com.ssafy.woojuin.domain.item;

import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.domain.item.dto.ItemResponse;
import com.ssafy.woojuin.domain.item.dto.ItemStatusResponse;
import com.ssafy.woojuin.global.common.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final S3Uploader s3Uploader;
    private final ItemQueueProducer itemQueueProducer;

    public ItemService(ItemRepository itemRepository, S3Uploader s3Uploader,
            ItemQueueProducer itemQueueProducer) {
        this.itemRepository = itemRepository;
        this.s3Uploader = s3Uploader;
        this.itemQueueProducer = itemQueueProducer;
    }

    @Transactional
    public ItemCreateResponse createFromRequest(Long workspaceId, Long userId, ItemCreateRequest request) {
        validate(request);

        Item item = Item.builder()
                .workspaceId(workspaceId)
                .createdBy(userId)
                .type(request.type())
                .url(request.url())
                .content(request.content())
                .build();

        return save(item, workspaceId);
    }

    @Transactional
    public ItemCreateResponse createFromImage(Long workspaceId, Long userId, MultipartFile file) {
        String s3Key = s3Uploader.upload(file, workspaceId);

        Item item = Item.builder()
                .workspaceId(workspaceId)
                .createdBy(userId)
                .type(ItemType.IMAGE)
                .s3Key(s3Key)
                .build();

        return save(item, workspaceId);
    }

    private ItemCreateResponse save(Item item, Long workspaceId) {
        Item saved = itemRepository.save(item);
        itemQueueProducer.publish(saved.getId(), workspaceId, saved.getType());
        return ItemCreateResponse.from(saved);
    }

    public ItemListResponse list(Long workspaceId, ItemType type, ItemStatus status, Boolean favorite,
            String sort, int page, int size) {
        Specification<Item> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("workspaceId"), workspaceId),
                cb.isNull(root.get("deletedAt")));
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (favorite != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("favorite"), favorite));
        }

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<ItemResponse> result = itemRepository.findAll(spec, pageable).map(ItemResponse::from);
        return ItemListResponse.from(result);
    }

    public ItemResponse getDetail(Long itemId) {
        return ItemResponse.from(findActiveItem(itemId));
    }

    public ItemStatusResponse getStatus(Long itemId) {
        return ItemStatusResponse.from(findActiveItem(itemId));
    }

    private Item findActiveItem(Long itemId) {
        return itemRepository.findById(itemId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ItemNotFoundException(itemId));
    }

    private Sort resolveSort(String sort) {
        if ("title".equalsIgnoreCase(sort)) {
            return Sort.by("title").ascending();
        }
        return Sort.by("createdAt").descending();
    }

    private void validate(ItemCreateRequest request) {
        switch (request.type()) {
            case URL -> {
                if (request.url() == null || request.url().isBlank()) {
                    throw new IllegalArgumentException("type=URL이면 url이 필수입니다");
                }
                if (request.content() != null && !request.content().isBlank()) {
                    throw new IllegalArgumentException("type=URL이면 content를 보낼 수 없습니다");
                }
            }
            case MEMO -> {
                if (request.content() == null || request.content().isBlank()) {
                    throw new IllegalArgumentException("type=MEMO이면 content가 필수입니다");
                }
                if (request.url() != null && !request.url().isBlank()) {
                    throw new IllegalArgumentException("type=MEMO이면 url을 보낼 수 없습니다");
                }
            }
            case IMAGE -> throw new IllegalArgumentException(
                    "type=IMAGE는 JSON이 아닌 multipart/form-data로 저장해야 합니다");
        }
    }
}
