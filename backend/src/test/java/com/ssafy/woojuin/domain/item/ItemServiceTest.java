package com.ssafy.woojuin.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.item.dto.ItemCreateRequest;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.global.common.ItemStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
}
