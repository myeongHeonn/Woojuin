package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService;
import com.ssafy.woojuin.domain.item.dto.ItemGeoResponse;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.repository.ItemGeoRow;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceNotFoundException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 뷰 조회 (FR-032). 좌표가 있는 활성 아이템을 페이지네이션 없이 한 번에 반환한다.
 *
 * <p>지도는 화면에 보이는 모든 핀을 한 번에 받아야 하므로 페이징이 오히려 방해가 된다.
 * 카테고리 필터도 서버에서 하지 않는다 — 프론트가 응답의 categoryIds로 즉시 필터링한다.
 */
@Slf4j
@Service
public class ItemGeoService {

    /**
     * 페이지네이션이 없으니 상한은 있어야 한다. 워크스페이스당 좌표 있는 아이템은 많아야
     * 수백 건 수준이라 실사용에서 걸릴 값이 아니지만, 걸리면 지도는 그리면서 경고를 남긴다.
     */
    private static final int MAX_GEO_ITEMS = 1000;

    private final ItemRepository itemRepository;
    private final ItemCategoryQueryService itemCategoryQueryService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;

    public ItemGeoService(ItemRepository itemRepository,
            ItemCategoryQueryService itemCategoryQueryService,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRepository workspaceRepository) {
        this.itemRepository = itemRepository;
        this.itemCategoryQueryService = itemCategoryQueryService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemGeoResponse> geoItems(Long workspaceId, Long userId) {
        verifyAccess(workspaceId, userId);

        List<ItemGeoRow> rows = itemRepository.findGeoRows(workspaceId, Limit.of(MAX_GEO_ITEMS));
        if (rows.size() == MAX_GEO_ITEMS) {
            log.warn("지도 조회 상한에 도달 — 일부 핀이 누락된다: workspaceId={}, limit={}",
                    workspaceId, MAX_GEO_ITEMS);
        }
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Long>> categoryIdsByItem = itemCategoryQueryService.categoryIdsByItemIds(
                rows.stream().map(ItemGeoRow::itemId).toList());

        return rows.stream()
                .map(row -> new ItemGeoResponse(row.itemId(), row.type(), row.title(),
                        categoryIdsByItem.getOrDefault(row.itemId(), List.of()),
                        row.favorite(), row.lat(), row.lng(), row.address()))
                .toList();
    }

    /**
     * 403(멤버 아님)과 404(워크스페이스 없음)를 구분한다. 멤버십을 먼저 조회하고 실패했을
     * 때만 존재 여부를 확인하므로 정상 경로에는 추가 쿼리가 없다.
     *
     * <p>참고: 기존 아이템 엔드포인트(ItemService.verifyMembership)는 없는 워크스페이스에도
     * 403을 준다. 지도 엔드포인트만 API 명세대로 둘을 구분하는 셈이라, 나중에 기존
     * 엔드포인트도 같은 방식으로 맞추는 게 좋다.
     */
    private void verifyAccess(Long workspaceId, Long userId) {
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId).isPresent()) {
            return;
        }
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new WorkspaceNotFoundException(workspaceId);
        }
        throw new WorkspaceAccessDeniedException(workspaceId);
    }
}
