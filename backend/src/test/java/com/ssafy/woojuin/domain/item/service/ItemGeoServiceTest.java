package com.ssafy.woojuin.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService;
import com.ssafy.woojuin.domain.item.dto.ItemGeoResponse;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.repository.ItemGeoRow;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceNotFoundException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

/**
 * 지도 조회 서비스 단위 테스트 (FR-032).
 *
 * <p>접근 제어(403/404 구분)와 응답 조립, 그리고 "빈 결과면 카테고리를 조회하지 않는다"는
 * 최적화가 여기서 검증된다.
 */
@ExtendWith(MockitoExtension.class)
class ItemGeoServiceTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long USER_ID = 10L;

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemCategoryQueryService itemCategoryQueryService;
    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;

    private ItemGeoService itemGeoService;

    @BeforeEach
    void setUp() {
        itemGeoService = new ItemGeoService(itemRepository, itemCategoryQueryService,
                workspaceMemberRepository, workspaceRepository);
    }

    private void asMember() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
    }

    private void asNonMember() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID))
                .thenReturn(Optional.empty());
    }

    private ItemGeoRow row(long itemId) {
        return row(itemId, false);
    }

    private ItemGeoRow row(long itemId, boolean favorite) {
        return new ItemGeoRow(itemId, ItemType.URL, "성수동 맛집", favorite, 37.5445, 127.0561,
                "서울 성동구 아차산로17길 49");
    }

    @Test
    void 좌표_보유_아이템을_지도_응답으로_조립한다() {
        asMember();
        when(itemRepository.findGeoRows(eq(WORKSPACE_ID), any(Limit.class)))
                .thenReturn(List.of(row(42L)));
        when(itemCategoryQueryService.categoryIdsByItemIds(anyCollection()))
                .thenReturn(Map.of(42L, List.of(1L, 2L)));

        List<ItemGeoResponse> result = itemGeoService.geoItems(WORKSPACE_ID, USER_ID);

        assertThat(result).hasSize(1);
        ItemGeoResponse pin = result.get(0);
        assertThat(pin.itemId()).isEqualTo(42L);
        assertThat(pin.type()).isEqualTo(ItemType.URL);
        assertThat(pin.title()).isEqualTo("성수동 맛집");
        assertThat(pin.categoryIds()).containsExactly(1L, 2L);
        assertThat(pin.favorite()).isFalse();
        assertThat(pin.lat()).isEqualTo(37.5445);
        assertThat(pin.lng()).isEqualTo(127.0561);
        assertThat(pin.address()).isEqualTo("서울 성동구 아차산로17길 49");
    }

    @Test
    void 즐겨찾기_여부를_그대로_내린다() {
        // 지도의 "즐겨찾기만 보기" 토글이 이 필드로 클라이언트에서 필터링한다.
        asMember();
        when(itemRepository.findGeoRows(eq(WORKSPACE_ID), any(Limit.class)))
                .thenReturn(List.of(row(42L, true), row(43L, false)));
        when(itemCategoryQueryService.categoryIdsByItemIds(anyCollection())).thenReturn(Map.of());

        List<ItemGeoResponse> result = itemGeoService.geoItems(WORKSPACE_ID, USER_ID);

        assertThat(result).extracting(ItemGeoResponse::favorite).containsExactly(true, false);
    }

    @Test
    void 카테고리가_없는_아이템은_빈_배열을_받는다() {
        asMember();
        when(itemRepository.findGeoRows(eq(WORKSPACE_ID), any(Limit.class)))
                .thenReturn(List.of(row(42L)));
        when(itemCategoryQueryService.categoryIdsByItemIds(anyCollection())).thenReturn(Map.of());

        List<ItemGeoResponse> result = itemGeoService.geoItems(WORKSPACE_ID, USER_ID);

        assertThat(result.get(0).categoryIds()).isEmpty();
    }

    @Test
    void 좌표_보유_아이템이_없으면_카테고리를_조회하지_않는다() {
        asMember();
        when(itemRepository.findGeoRows(eq(WORKSPACE_ID), any(Limit.class))).thenReturn(List.of());

        List<ItemGeoResponse> result = itemGeoService.geoItems(WORKSPACE_ID, USER_ID);

        assertThat(result).isEmpty();
        verifyNoInteractions(itemCategoryQueryService);
    }

    @Test
    void 상한을_걸어_조회한다() {
        asMember();
        when(itemRepository.findGeoRows(eq(WORKSPACE_ID), eq(Limit.of(1000)))).thenReturn(List.of());

        assertThat(itemGeoService.geoItems(WORKSPACE_ID, USER_ID)).isEmpty();
    }

    @Test
    void 멤버가_아니면_403() {
        asNonMember();
        when(workspaceRepository.existsById(WORKSPACE_ID)).thenReturn(true);

        assertThatThrownBy(() -> itemGeoService.geoItems(WORKSPACE_ID, USER_ID))
                .isInstanceOf(WorkspaceAccessDeniedException.class);

        verifyNoInteractions(itemRepository, itemCategoryQueryService);
    }

    @Test
    void 워크스페이스가_없으면_404() {
        asNonMember();
        when(workspaceRepository.existsById(WORKSPACE_ID)).thenReturn(false);

        assertThatThrownBy(() -> itemGeoService.geoItems(WORKSPACE_ID, USER_ID))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void 멤버일_때는_워크스페이스_존재_확인을_하지_않는다() {
        // 403/404 구분 때문에 정상 경로에 쿼리가 늘어나면 안 된다.
        asMember();
        when(itemRepository.findGeoRows(eq(WORKSPACE_ID), any(Limit.class))).thenReturn(List.of());

        itemGeoService.geoItems(WORKSPACE_ID, USER_ID);

        verifyNoInteractions(workspaceRepository);
    }
}
