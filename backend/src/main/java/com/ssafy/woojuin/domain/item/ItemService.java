package com.ssafy.woojuin.domain.item;

import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.domain.item.dto.ItemResponse;
import com.ssafy.woojuin.domain.item.dto.ItemStatusResponse;
import com.ssafy.woojuin.domain.item.dto.ItemUpdateRequest;
import com.ssafy.woojuin.domain.category.dto.CategoryResponse;
import com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.util.List;
import java.util.Map;
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
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ItemCategoryQueryService itemCategoryQueryService;

    public ItemService(ItemRepository itemRepository, S3Uploader s3Uploader,
            ItemQueueProducer itemQueueProducer, WorkspaceMemberRepository workspaceMemberRepository,
            ItemCategoryQueryService itemCategoryQueryService) {
        this.itemRepository = itemRepository;
        this.s3Uploader = s3Uploader;
        this.itemQueueProducer = itemQueueProducer;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.itemCategoryQueryService = itemCategoryQueryService;
    }

    public ItemCreateResponse createFromRequest(Long workspaceId, Long userId, ItemCreateRequest request) {
        verifyMembership(workspaceId, userId);
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
        verifyMembership(workspaceId, userId);

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
    public ItemListResponse list(Long workspaceId, Long userId, ItemType type, ItemStatus status, Boolean favorite,
            String sort, int page, int size) {
        verifyMembership(workspaceId, userId);

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
        return toListResponse(itemRepository.findAll(spec, pageable));
    }

    @Transactional(readOnly = true)
    public ItemResponse getDetail(Long itemId, Long userId) {
        Item item = findActiveItem(itemId, userId);
        return ItemResponse.from(item, itemCategoryQueryService.categoriesOf(item.getId()));
    }

    @Transactional(readOnly = true)
    public ItemStatusResponse getStatus(Long itemId, Long userId) {
        return ItemStatusResponse.from(findActiveItem(itemId, userId));
    }

    /**
     * 제목·메모 수정. 수정해도 AI 재처리 큐에는 발행하지 않는다 — 사용자가 직접 고친
     * 내용을 AI가 다시 덮어쓰면 안 되기 때문. 재처리 정책은 AI 로직을 만드는
     * 묶음 D/F와 합의해서 정할 것.
     * 태그·카테고리 수정(API 명세서)은 해당 도메인이 아직 없어 이번 범위 밖.
     */
    @Transactional
    public ItemResponse update(Long itemId, Long userId, ItemUpdateRequest request) {
        if (request.title() == null && request.content() == null) {
            throw new IllegalArgumentException("수정할 내용이 없습니다 (title 또는 content 필요)");
        }
        Item item = findActiveItem(itemId, userId);
        item.update(request.title(), request.content());
        return ItemResponse.from(item, itemCategoryQueryService.categoriesOf(item.getId()));
    }

    /** 삭제는 항상 휴지통 이동이 먼저다 (AGENTS.md 도메인 규칙). */
    @Transactional
    public void moveToTrash(Long itemId, Long userId) {
        findActiveItem(itemId, userId).moveToTrash();
    }

    @Transactional(readOnly = true)
    public ItemListResponse listTrash(Long workspaceId, Long userId, int page, int size) {
        verifyMembership(workspaceId, userId);

        Specification<Item> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("workspaceId"), workspaceId),
                cb.isNotNull(root.get("deletedAt")));

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by("deletedAt").descending());
        return toListResponse(itemRepository.findAll(spec, pageable));
    }

    @Transactional
    public ItemResponse restore(Long itemId, Long userId) {
        Item item = findTrashedItem(itemId, userId);
        item.restore();
        return ItemResponse.from(item, itemCategoryQueryService.categoriesOf(item.getId()));
    }

    /** 페이지의 아이템들에 카테고리를 배치로 채워 응답으로 변환한다(N+1 방지). */
    private ItemListResponse toListResponse(Page<Item> items) {
        Map<Long, List<CategoryResponse>> categoriesByItem = itemCategoryQueryService.categoriesByItemIds(
                items.getContent().stream().map(Item::getId).toList());
        Page<ItemResponse> mapped = items.map(item ->
                ItemResponse.from(item, categoriesByItem.getOrDefault(item.getId(), List.of())));
        return ItemListResponse.from(mapped);
    }

    /**
     * 영구 삭제는 휴지통에 있는 것만 가능하다. DB를 먼저 지우고 S3 원본을 지우는데,
     * 순서를 바꾸면 S3만 지워지고 DB가 남아 "복구했더니 이미지가 없는" 상태가 될 수
     * 있다. 반대로 이 순서라면 최악이라도 S3에 고아 파일이 남을 뿐이다.
     */
    public void deletePermanently(Long itemId, Long userId) {
        Item item = findTrashedItem(itemId, userId);
        String s3Key = item.getS3Key();

        itemRepository.delete(item);

        if (s3Key != null) {
            s3Uploader.deleteQuietly(s3Key);
        }
    }

    /** itemId만으로 접근하는 API용. 아이템이 속한 workspaceId를 먼저 알아낸 뒤 멤버십을 검증한다. */
    private Item findActiveItem(Long itemId, Long userId) {
        Item item = itemRepository.findById(itemId)
                .filter(i -> !i.isTrashed())
                .orElseThrow(() -> new ItemNotFoundException(itemId));
        verifyMembership(item.getWorkspaceId(), userId);
        return item;
    }

    private Item findTrashedItem(Long itemId, Long userId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));
        verifyMembership(item.getWorkspaceId(), userId);
        if (!item.isTrashed()) {
            throw new IllegalArgumentException("휴지통에 있는 아이템만 복구하거나 영구 삭제할 수 있습니다");
        }
        return item;
    }

    private void verifyMembership(Long workspaceId, Long userId) {
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceAccessDeniedException(workspaceId));
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
