package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService;
import com.ssafy.woojuin.domain.item.dto.ItemCoordinateResponse;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository.CoordinateRow;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceNotFoundException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우주(3D) 뷰 조회. 좌표가 계산된 활성 아이템을 페이지네이션 없이 한 번에 반환한다 —
 * 지도 뷰(ItemGeoService)와 같은 이유로, 화면의 모든 별을 한 번에 받아야 한다.
 *
 * <p>임베딩이 아직 없는 아이템(AI 보강 실패, 임베딩 도입 전 저장)은 응답에 없다.
 */
@Service
public class ItemCoordinatesService {

    private final ItemEmbeddingJdbcRepository embeddingRepository;
    private final ItemCategoryQueryService itemCategoryQueryService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;

    public ItemCoordinatesService(ItemEmbeddingJdbcRepository embeddingRepository,
            ItemCategoryQueryService itemCategoryQueryService,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRepository workspaceRepository) {
        this.embeddingRepository = embeddingRepository;
        this.itemCategoryQueryService = itemCategoryQueryService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemCoordinateResponse> coordinates(Long workspaceId, Long userId) {
        verifyAccess(workspaceId, userId);

        List<CoordinateRow> rows = embeddingRepository.findCoordinates(workspaceId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> categoryIdsByItem = itemCategoryQueryService.categoryIdsByItemIds(
                rows.stream().map(CoordinateRow::itemId).toList());

        return rows.stream()
                .map(row -> new ItemCoordinateResponse(row.itemId(), row.type(), row.title(),
                        categoryIdsByItem.getOrDefault(row.itemId(), List.of()),
                        row.favorite(), row.x(), row.y(), row.z()))
                .toList();
    }

    /** 403(멤버 아님)/404(워크스페이스 없음) 구분 — ItemGeoService.verifyAccess와 동일 규칙. */
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
