package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.ai.usage.AiUsageService;
import com.ssafy.woojuin.domain.item.dto.ItemCreateResponse;
import com.ssafy.woojuin.domain.item.dto.PlaceSaveRequest;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.category.service.CategoryAssignmentService;
import com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.global.common.ItemStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 장소 아이템 생성 (FR-053) — 핵심 계약은 "AI 파이프라인을 태우지 않는다"이다.
 * 큐 발행·사용량 차감이 슬쩍 끼어들면 워치 저장마다 AI 쿼터가 줄어드는 회귀가 된다.
 */
@ExtendWith(MockitoExtension.class)
class ItemServiceCreatePlaceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemQueueProducer itemQueueProducer;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private AiUsageService aiUsageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private S3Uploader s3Uploader;

    @Mock
    private ItemCategoryQueryService itemCategoryQueryService;

    @Mock
    private CategoryAssignmentService categoryAssignmentService;

    @Mock
    private ItemSummaryAssembler itemSummaryAssembler;

    @InjectMocks
    private ItemService itemService;

    private static final PlaceSaveRequest REQUEST =
            new PlaceSaveRequest("온화정", 37.5445, 127.0561, "서울 성동구 성수동2가", null);

    @Test
    @DisplayName("장소 저장은 좌표·주소가 실린 완성형(DONE) 아이템을 만든다 — AI 큐·사용량 없이")
    void createPlace_savesDoneItemWithLocation_withoutAiPipeline() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemCreateResponse response = itemService.createPlace(10L, 1L, REQUEST);

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(saved.capture());
        Item item = saved.getValue();
        assertThat(item.getType()).isEqualTo(ItemType.MEMO);
        assertThat(item.getTitle()).isEqualTo("온화정");
        assertThat(item.getStatus()).isEqualTo(ItemStatus.DONE);
        assertThat(item.getLat()).isEqualTo(37.5445);
        assertThat(item.getLng()).isEqualTo(127.0561);
        assertThat(item.getAddress()).isEqualTo("서울 성동구 성수동2가");
        assertThat(response.status()).isEqualTo(ItemStatus.DONE);

        // AI 파이프라인 미개입 — 이게 이 경로의 존재 이유다
        verifyNoInteractions(itemQueueProducer, aiUsageService);
        // publishEvent 는 오버로드가 둘이라 any() 만으로는 다른 시그니처에 붙는다 — Object 로 고정
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("멤버가 아닌 워크스페이스에는 저장할 수 없다")
    void createPlace_notMember_denied() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.createPlace(10L, 1L, REQUEST))
                .isInstanceOf(WorkspaceAccessDeniedException.class);
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("범위 밖 좌표는 400 — applyLocation 이 조용히 버리기 전에 거른다")
    void createPlace_invalidCoordinates_throws() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));

        assertThatThrownBy(() -> itemService.createPlace(
                10L, 1L, new PlaceSaveRequest("이상한 곳", 123.0, 999.0, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(itemRepository, never()).save(any());
    }
}
