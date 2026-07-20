package com.ssafy.woojuin.domain.item;

import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
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
