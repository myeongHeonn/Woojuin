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

    /** 한 번에 조회 가능한 최대 건수. 클라이언트가 size를 크게 보내도 여기서 잘린다. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ItemRepository itemRepository;
    private final S3Uploader s3Uploader;
    private final ItemQueueProducer itemQueueProducer;

    public ItemService(ItemRepository itemRepository, S3Uploader s3Uploader,
            ItemQueueProducer itemQueueProducer) {
        this.itemRepository = itemRepository;
        this.s3Uploader = s3Uploader;
        this.itemQueueProducer = itemQueueProducer;
    }

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

    public ItemCreateResponse createFromImage(Long workspaceId, Long userId, MultipartFile file) {
        // S3 업로드는 느린 네트워크 I/O라 트랜잭션 밖에서 먼저 끝낸다. 트랜잭션 안에서
        // 하면 업로드가 끝날 때까지 DB 커넥션을 붙잡고 있어 풀이 마른다.
        String s3Key = s3Uploader.upload(file, workspaceId);

        Item item = Item.builder()
                .workspaceId(workspaceId)
                .createdBy(userId)
                .type(ItemType.IMAGE)
                .s3Key(s3Key)
                .build();

        return save(item, workspaceId);
    }

    /**
     * 저장은 단일 save라 별도 @Transactional을 걸지 않는다 (repository.save가 자체
     * 트랜잭션으로 커밋). 덕분에 큐 발행 시점에는 이미 행이 커밋돼 있어 컨슈머가
     * 바로 조회할 수 있다.
     * 나중에 태그 등 저장이 하나 더 늘어 원자성이 필요해지면 이 메서드를 감싸는
     * @Transactional을 추가할 것 — 그때도 큐 발행은 ItemQueueProducer가 커밋 이후로
     * 미뤄주므로 순서는 계속 안전하다.
     */
    private ItemCreateResponse save(Item item, Long workspaceId) {
        Item saved = itemRepository.save(item);
        itemQueueProducer.publish(saved.getId(), workspaceId, saved.getType());
        return ItemCreateResponse.from(saved);
    }

    @Transactional(readOnly = true)
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

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), resolveSort(sort));
        Page<ItemResponse> result = itemRepository.findAll(spec, pageable).map(ItemResponse::from);
        return ItemListResponse.from(result);
    }

    @Transactional(readOnly = true)
    public ItemResponse getDetail(Long itemId) {
        return ItemResponse.from(findActiveItem(itemId));
    }

    @Transactional(readOnly = true)
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
