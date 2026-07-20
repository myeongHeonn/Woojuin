package com.ssafy.woojuin.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.dto.ItemListResponse;
import com.ssafy.woojuin.domain.item.dto.ItemResponse;
import com.ssafy.woojuin.domain.item.dto.ItemStatusResponse;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

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
}
