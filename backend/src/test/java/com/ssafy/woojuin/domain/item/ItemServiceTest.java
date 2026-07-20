package com.ssafy.woojuin.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.domain.item.dto.ItemResponse;
import com.ssafy.woojuin.domain.item.dto.ItemStatusResponse;
import com.ssafy.woojuin.domain.item.dto.ItemUpdateRequest;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private S3Uploader s3Uploader;

    @Mock
    private ItemQueueProducer itemQueueProducer;

    @InjectMocks
    private ItemService itemService;

    @Test
    void URL_타입은_url이_없으면_예외() {
        ItemCreateRequest request = new ItemCreateRequest(ItemType.URL, null, null);

        assertThatThrownBy(() -> itemService.createFromRequest(1L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void URL_타입에_content를_같이_보내면_예외() {
        ItemCreateRequest request = new ItemCreateRequest(ItemType.URL, "https://example.com", "메모");

        assertThatThrownBy(() -> itemService.createFromRequest(1L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void MEMO_타입은_content가_없으면_예외() {
        ItemCreateRequest request = new ItemCreateRequest(ItemType.MEMO, null, null);

        assertThatThrownBy(() -> itemService.createFromRequest(1L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void URL_저장_성공시_큐에_발행되고_PROCESSING_응답() {
        ItemCreateRequest request = new ItemCreateRequest(ItemType.URL, "https://example.com", null);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemCreateResponse response = itemService.createFromRequest(10L, 1L, request);

        assertThat(response.status()).isEqualTo(ItemStatus.PROCESSING);
        verify(itemQueueProducer).publish(response.itemId(), 10L, ItemType.URL);
    }

    @Test
    void IMAGE_저장은_S3_업로드_후_큐에_발행() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
        when(s3Uploader.upload(file, 10L)).thenReturn("items/10/uuid-photo.png");
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemCreateResponse response = itemService.createFromImage(10L, 1L, file);

        assertThat(response.status()).isEqualTo(ItemStatus.PROCESSING);
        verify(itemQueueProducer).publish(response.itemId(), 10L, ItemType.IMAGE);
    }

    @Test
    void 상세조회는_존재하는_아이템을_반환() {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.URL)
                .url("https://example.com").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        ItemResponse response = itemService.getDetail(1L);

        assertThat(response.type()).isEqualTo(ItemType.URL);
        assertThat(response.url()).isEqualTo("https://example.com");
    }

    @Test
    void 상세조회는_없는_아이템이면_404_예외() {
        when(itemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getDetail(999L))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void 상태조회는_상태값만_반환() {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.MEMO)
                .content("메모").build();
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item));

        ItemStatusResponse response = itemService.getStatus(2L);

        assertThat(response.status()).isEqualTo(ItemStatus.PROCESSING);
    }

    @Test
    void 목록조회는_페이지_결과를_그대로_매핑() {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.URL)
                .url("https://example.com").build();
        when(itemRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        ItemListResponse response = itemService.list(1L, null, null, null, "latest", 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void 목록조회_size가_상한을_넘으면_잘린다() {
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        when(itemRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        itemService.list(1L, null, null, null, "latest", 0, 100_000);

        verify(itemRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void 소프트삭제된_아이템은_상세조회에서_404() {
        Item deleted = trashedItem();
        when(itemRepository.findById(5L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> itemService.getDetail(5L))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void 수정은_null이_아닌_필드만_반영() {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.MEMO)
                .title("원래 제목").content("원래 내용").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        ItemResponse response = itemService.update(1L, new ItemUpdateRequest("바뀐 제목", null));

        assertThat(response.title()).isEqualTo("바뀐 제목");
        assertThat(response.content()).isEqualTo("원래 내용");
    }

    @Test
    void URL_아이템에도_메모를_붙일_수_있다() {
        // 트랙 A/B가 모두 실패하면 사용자 메모로 폴백하기 때문 (FR-020)
        Item urlItem = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.URL)
                .url("https://example.com").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(urlItem));

        ItemResponse response = itemService.update(1L, new ItemUpdateRequest(null, "직접 남긴 메모"));

        assertThat(response.content()).isEqualTo("직접 남긴 메모");
        assertThat(response.url()).isEqualTo("https://example.com");
    }

    @Test
    void 수정할_내용이_하나도_없으면_예외() {
        assertThatThrownBy(() -> itemService.update(1L, new ItemUpdateRequest(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 수정은_큐에_재발행하지_않는다() {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.MEMO)
                .content("원래").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        itemService.update(1L, new ItemUpdateRequest(null, "수정됨"));

        verifyNoInteractions(itemQueueProducer);
    }

    @Test
    void 삭제는_행을_지우지_않고_휴지통으로_보낸다() {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.MEMO)
                .content("메모").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        itemService.moveToTrash(1L);

        assertThat(item.isTrashed()).isTrue();
        verify(itemRepository, never()).delete(any(Item.class));
    }

    @Test
    void 휴지통에_없는_아이템은_복구할_수_없다() {
        Item active = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.MEMO)
                .content("정상").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> itemService.restore(1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 복구하면_다시_조회된다() {
        Item trashed = trashedItem();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(trashed));

        ItemResponse response = itemService.restore(1L);

        assertThat(trashed.isTrashed()).isFalse();
        assertThat(response.deletedAt()).isNull();
    }

    @Test
    void 휴지통에_없는_아이템은_영구삭제할_수_없다() {
        Item active = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.MEMO)
                .content("정상").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> itemService.deletePermanently(1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(itemRepository, never()).delete(any(Item.class));
    }

    @Test
    void 이미지_영구삭제는_S3_원본도_지운다() {
        Item trashed = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.IMAGE)
                .s3Key("items/1/uuid-photo.png").build();
        ReflectionTestUtils.setField(trashed, "deletedAt", Instant.now());
        when(itemRepository.findById(1L)).thenReturn(Optional.of(trashed));

        itemService.deletePermanently(1L);

        verify(itemRepository).delete(trashed);
        verify(s3Uploader).deleteQuietly("items/1/uuid-photo.png");
    }

    @Test
    void s3Key가_없는_아이템_영구삭제는_S3를_호출하지_않는다() {
        Item trashed = trashedItem();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(trashed));

        itemService.deletePermanently(1L);

        verify(itemRepository).delete(trashed);
        verifyNoInteractions(s3Uploader);
    }

    private Item trashedItem() {
        Item item = Item.builder().workspaceId(1L).createdBy(1L).type(ItemType.MEMO)
                .content("삭제됨").build();
        ReflectionTestUtils.setField(item, "deletedAt", Instant.now());
        return item;
    }
}
