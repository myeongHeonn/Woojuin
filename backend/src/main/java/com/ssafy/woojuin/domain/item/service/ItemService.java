package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.ai.usage.AiUsageReservation;
import com.ssafy.woojuin.domain.ai.usage.AiUsageService;
import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.dto.ItemDetailResponse;
import com.ssafy.woojuin.domain.item.dto.ItemFavoriteResponse;
import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.domain.item.dto.ItemStatusResponse;
import com.ssafy.woojuin.domain.item.dto.ItemUpdateRequest;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.exception.ItemNotFoundException;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.category.service.CategoryAssignmentService;
import com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.global.common.ItemStatus;
import com.ssafy.woojuin.global.sse.WorkspaceChangedEvent;
import com.ssafy.woojuin.global.sse.WorkspaceEventType;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
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
    private final CategoryAssignmentService categoryAssignmentService;
    private final ItemSummaryAssembler itemSummaryAssembler;
    private final AiUsageService aiUsageService;
    private final ApplicationEventPublisher eventPublisher;

    public ItemService(ItemRepository itemRepository, S3Uploader s3Uploader,
            ItemQueueProducer itemQueueProducer, WorkspaceMemberRepository workspaceMemberRepository,
            ItemCategoryQueryService itemCategoryQueryService,
            CategoryAssignmentService categoryAssignmentService, ItemSummaryAssembler itemSummaryAssembler,
            AiUsageService aiUsageService, ApplicationEventPublisher eventPublisher) {
        this.itemRepository = itemRepository;
        this.s3Uploader = s3Uploader;
        this.itemQueueProducer = itemQueueProducer;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.itemCategoryQueryService = itemCategoryQueryService;
        this.categoryAssignmentService = categoryAssignmentService;
        this.itemSummaryAssembler = itemSummaryAssembler;
        this.aiUsageService = aiUsageService;
        this.eventPublisher = eventPublisher;
    }

    public ItemCreateResponse createFromRequest(Long workspaceId, Long userId, ItemCreateRequest request) {
        verifyMembership(workspaceId, userId);
        validate(request);
        AiUsageReservation usageReservation = aiUsageService.reserveForItemCreation(workspaceId);

        Item item = Item.builder()
                .workspaceId(workspaceId)
                .createdBy(userId)
                .type(request.type())
                .url(request.url())
                .content(request.content())
                .build();

        return save(item, workspaceId, usageReservation);
    }

    public ItemCreateResponse createFromImage(Long workspaceId, Long userId, MultipartFile file) {
        verifyMembership(workspaceId, userId);
        AiUsageReservation usageReservation = aiUsageService.reserveForItemCreation(workspaceId);

        // S3 업로드는 느린 네트워크 I/O라 트랜잭션 밖에서 먼저 끝낸다. 트랜잭션 안에서
        // 하면 업로드가 끝날 때까지 DB 커넥션을 붙잡고 있어 풀이 마른다.
        String s3Key;
        try {
            s3Key = s3Uploader.upload(file, workspaceId);
        } catch (RuntimeException e) {
            aiUsageService.release(usageReservation);
            throw e;
        }

        Item item = Item.builder()
                .workspaceId(workspaceId)
                .createdBy(userId)
                .type(ItemType.IMAGE)
                .title(originalFilenameOrNull(file))
                .s3Key(s3Key)
                .build();

        // DB 저장이 실패하면 방금 올린 S3 원본이 고아로 남는다 — 저장 실패 시에만
        // 보상 삭제한다(publish 실패는 이미 커밋된 뒤라 대상이 아님, save(Item,Long,Runnable) 참고).
        return save(
                item, workspaceId, usageReservation,
                () -> s3Uploader.deleteQuietly(s3Key));
    }

    /**
     * OCR로 텍스트를 못 뽑으면(풍경 사진 등) AiAnalyzer에 넘길 게 아무것도 없어 계속
     * "기타"로만 분류된다. 원본 파일명을 title 기본값으로 채워 최소한의 분류 힌트를 준다.
     */
    private String originalFilenameOrNull(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name != null && !name.isBlank()) ? name : null;
    }

    /**
     * 저장은 단일 save라 별도 @Transactional을 걸지 않는다 (repository.save가 자체
     * 트랜잭션으로 커밋). 덕분에 큐 발행 시점에는 이미 행이 커밋돼 있어 컨슈머가
     * 바로 조회할 수 있다.
     * 나중에 태그 등 저장이 하나 더 늘어 원자성이 필요해지면 이 메서드를 감싸는
     * @Transactional을 추가할 것 — 그때도 큐 발행은 ItemQueueProducer가 커밋 이후로
     * 미뤄주므로 순서는 계속 안전하다.
     */
    private ItemCreateResponse save(
            Item item, Long workspaceId, AiUsageReservation usageReservation) {
        return save(item, workspaceId, usageReservation, () -> { });
    }

    /**
     * onSaveFailure는 DB 저장 자체가 실패했을 때만 실행된다(예: IMAGE의 S3 원본 정리).
     * 저장은 됐는데 큐 발행이 실패한 경우는 이미 행이 커밋된 뒤라 여기서 건드리면 안
     * 된다 — DB엔 아이템이 있는데 참조하는 S3 원본이 지워지는 더 나쁜 상태가 된다.
     */
    private ItemCreateResponse save(
            Item item,
            Long workspaceId,
            AiUsageReservation usageReservation,
            Runnable onSaveFailure) {
        Item saved;
        try {
            saved = itemRepository.save(item);
        } catch (RuntimeException e) {
            onSaveFailure.run();
            aiUsageService.release(usageReservation);
            throw e;
        }
        // DB에 PROCESSING 아이템이 생긴 뒤에는 큐 발행이 실패해도 사용량을 유지한다.
        // StuckItemRepublisher가 나중에 다시 발행하며 큐 재시도는 추가 차감하지 않는다.
        aiUsageService.commit(usageReservation);
        itemQueueProducer.publish(saved.getId(), workspaceId, saved.getType());
        publishItemChanged(workspaceId);
        return ItemCreateResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ItemListResponse list(Long workspaceId, Long userId, ItemType type, ItemStatus status, Boolean favorite,
            List<Long> categoryIds, String sort, int page, int size) {
        verifyMembership(workspaceId, userId);

        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), resolveSort(sort));

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
        if (categoryIds != null && !categoryIds.isEmpty()) {
            // 카테고리는 JPA 연관관계 없는 조인 테이블이라, 선택된 카테고리들 중 하나라도(OR)
            // 연결된 아이템 id를 먼저 뽑아 id IN 조건으로 필터한다. 연결된 아이템이 없으면
            // 빈 결과를 그대로 반환한다.
            List<Long> itemIds = itemCategoryQueryService.itemIdsInCategories(categoryIds);
            if (itemIds.isEmpty()) {
                return toListResponse(Page.empty(pageable));
            }
            spec = spec.and((root, query, cb) -> root.get("id").in(itemIds));
        }

        return toListResponse(itemRepository.findAll(spec, pageable));
    }

    @Transactional(readOnly = true)
    public ItemDetailResponse getDetail(Long itemId, Long userId) {
        Item item = findActiveItem(itemId, userId);
        return ItemDetailResponse.from(item, itemCategoryQueryService.categoriesOf(item.getId()), imageUrlOf(item));
    }

    @Transactional(readOnly = true)
    public ItemStatusResponse getStatus(Long itemId, Long userId) {
        return ItemStatusResponse.from(findActiveItem(itemId, userId));
    }

    /**
     * 제목·메모·카테고리 수정. 수정해도 AI 재처리 큐에는 발행하지 않는다 — 사용자가 직접
     * 고친 내용을 AI가 다시 덮어쓰면 안 되기 때문. 재처리 정책은 AI 로직을 만드는
     * 묶음 D/F와 합의해서 정할 것.
     *
     * <p>categoryIds는 보낸 집합으로 교체한다(추가가 아니다). null이면 카테고리를 건드리지
     * 않고, 빈 배열은 ItemUpdateRequest의 {@code @Size(min = 1)}에서 400으로 걸린다.
     * 카테고리 교체가 제목·메모 수정과 한 트랜잭션에 묶여 있어, 카테고리 id가 하나라도
     * 잘못되면 제목까지 함께 롤백된다(반쪽 저장 방지).
     *
     * <p>태그 수정은 태그 기능 자체를 구현하지 않기로 결정되어 대상에서 제외됐다.
     */
    @Transactional
    public ItemDetailResponse update(Long itemId, Long userId, ItemUpdateRequest request) {
        if (request.title() == null && request.content() == null && request.categoryIds() == null) {
            throw new IllegalArgumentException("수정할 내용이 없습니다 (title, content, categoryIds 중 하나 필요)");
        }
        Item item = findActiveItem(itemId, userId);
        item.update(request.title(), request.content());
        if (request.categoryIds() != null) {
            categoryAssignmentService.replace(item.getId(), item.getWorkspaceId(), request.categoryIds());
        }
        publishItemChanged(item.getWorkspaceId());
        return ItemDetailResponse.from(item, itemCategoryQueryService.categoriesOf(item.getId()), imageUrlOf(item));
    }

    /**
     * 즐겨찾기 등록(favorite=true)/해제(false). 서버가 토글하지 않고 클라이언트가 원하는
     * 상태를 메서드로 지정하는 방식이라 멱등하다 — 별을 연타해도 서버 상태가 요청 순서에
     * 따라 뒤집히지 않는다.
     * 휴지통에 있는 아이템은 findActiveItem에서 걸러 404다 — 목록에 안 보이는 것을
     * 즐겨찾기해 두면 복구 전까지 어디에도 나타나지 않아 사용자가 이유를 알 수 없다.
     */
    @Transactional
    public ItemFavoriteResponse changeFavorite(Long itemId, Long userId, boolean favorite) {
        Item item = findActiveItem(itemId, userId);
        item.changeFavorite(favorite);
        publishItemChanged(item.getWorkspaceId());
        return ItemFavoriteResponse.from(item);
    }

    /** 삭제는 항상 휴지통 이동이 먼저다 (AGENTS.md 도메인 규칙). */
    @Transactional
    public void moveToTrash(Long itemId, Long userId) {
        Item item = findActiveItem(itemId, userId);
        item.moveToTrash();
        publishItemChanged(item.getWorkspaceId());
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
    public ItemDetailResponse restore(Long itemId, Long userId) {
        Item item = findTrashedItem(itemId, userId);
        item.restore();
        publishItemChanged(item.getWorkspaceId());
        return ItemDetailResponse.from(item, itemCategoryQueryService.categoriesOf(item.getId()), imageUrlOf(item));
    }

    /** 검색과 카드 형태가 갈라지지 않도록 조립은 ItemSummaryAssembler에 위임한다. */
    private ItemListResponse toListResponse(Page<Item> items) {
        return itemSummaryAssembler.toListResponse(items);
    }

    /**
     * 상세용 IMAGE presigned URL — 항상 원본. URL/MEMO는 S3 원본이 없어 null.
     * 만료가 있는 URL이라 컬럼에 저장하지 않는다 — 발급과 캐시는 S3Uploader.presignGet이 책임진다.
     */
    private String imageUrlOf(Item item) {
        if (item.getType() == ItemType.IMAGE && item.getS3Key() != null) {
            return s3Uploader.presignGet(item.getS3Key());
        }
        return null;
    }

    /**
     * 영구 삭제는 휴지통에 있는 것만 가능하다. DB를 먼저 지우고 S3 원본을 지우는데,
     * 순서를 바꾸면 S3만 지워지고 DB가 남아 "복구했더니 이미지가 없는" 상태가 될 수
     * 있다. 반대로 이 순서라면 최악이라도 S3에 고아 파일이 남을 뿐이다.
     */
    public void deletePermanently(Long itemId, Long userId) {
        Item item = findTrashedItem(itemId, userId);
        String s3Key = item.getS3Key();
        String thumbnailS3Key = item.getThumbnailS3Key();

        itemRepository.delete(item);
        publishItemChanged(item.getWorkspaceId());

        // 원본과 썸네일 둘 다 정리한다(썸네일은 IMAGE·URL이 생성에 성공했을 때만 존재).
        if (s3Key != null) {
            s3Uploader.deleteQuietly(s3Key);
        }
        if (thumbnailS3Key != null) {
            s3Uploader.deleteQuietly(thumbnailS3Key);
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

    private void publishItemChanged(Long workspaceId) {
        eventPublisher.publishEvent(WorkspaceChangedEvent.of(workspaceId, WorkspaceEventType.ITEM));
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
