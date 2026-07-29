package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.category.CategoryDefaults;
import com.ssafy.woojuin.domain.category.entity.Category;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import com.ssafy.woojuin.domain.category.service.ItemCategoryQueryService;
import com.ssafy.woojuin.domain.item.dto.UniverseResponse;
import com.ssafy.woojuin.domain.item.dto.UniverseResponse.ConstellationResponse;
import com.ssafy.woojuin.domain.item.dto.UniverseResponse.StarResponse;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository;
import com.ssafy.woojuin.domain.item.repository.ItemEmbeddingJdbcRepository.CoordinateRow;
import com.ssafy.woojuin.domain.workspace.exception.WorkspaceNotFoundException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우주(3D) 뷰 조회. 좌표가 계산된 활성 아이템을 카테고리(별자리)별로 묶어 한 번에
 * 반환한다 — 프론트 씬이 이 구조를 그대로 순회하며 그린다(페이지네이션 없음, 지도 뷰와
 * 같은 이유).
 *
 * <p>규칙(프론트 목업 계약과 동일):
 * <ul>
 *   <li>여러 카테고리에 속한 아이템은 각 별자리에 중복으로 들어간다</li>
 *   <li>"기타"는 별자리가 아니다 — 기타에만 속한 별은 unclassified로 홀로 뜬다.
 *       기타 + 실제 카테고리에 함께 속하면 실제 별자리에만 나온다</li>
 *   <li>임베딩이 아직 없는 아이템(AI 보강 실패, 임베딩 도입 전 저장)은 응답에 없다</li>
 * </ul>
 */
@Service
public class UniverseService {

    private final ItemEmbeddingJdbcRepository embeddingRepository;
    private final ItemCategoryQueryService itemCategoryQueryService;
    private final CategoryRepository categoryRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;

    public UniverseService(ItemEmbeddingJdbcRepository embeddingRepository,
            ItemCategoryQueryService itemCategoryQueryService,
            CategoryRepository categoryRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRepository workspaceRepository) {
        this.embeddingRepository = embeddingRepository;
        this.itemCategoryQueryService = itemCategoryQueryService;
        this.categoryRepository = categoryRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public UniverseResponse universe(Long workspaceId, Long userId) {
        verifyAccess(workspaceId, userId);

        List<CoordinateRow> rows = embeddingRepository.findCoordinates(workspaceId);
        if (rows.isEmpty()) {
            return new UniverseResponse(List.of(), List.of());
        }

        Map<Long, List<Long>> categoryIdsByItem = itemCategoryQueryService.categoryIdsByItemIds(
                rows.stream().map(CoordinateRow::itemId).toList());
        List<Category> categories = categoryRepository.findByWorkspaceId(workspaceId).stream()
                .sorted(Comparator.comparing(Category::getId))
                .toList();

        Map<Long, List<StarResponse>> starsByCategory = new LinkedHashMap<>();
        List<StarResponse> unclassified = new ArrayList<>();

        for (CoordinateRow row : rows) {
            StarResponse star = toStar(row);
            List<Long> realCategoryIds = categoryIdsByItem.getOrDefault(row.itemId(), List.of())
                    .stream()
                    .filter(id -> !isEtc(categories, id))
                    .toList();
            if (realCategoryIds.isEmpty()) {
                unclassified.add(star);
                continue;
            }
            for (Long categoryId : realCategoryIds) {
                starsByCategory.computeIfAbsent(categoryId, k -> new ArrayList<>()).add(star);
            }
        }

        // 빈 별자리는 넣지 않는다 — 아이템 없는 별자리는 위치를 정할 수 없어 프론트도 건너뛴다.
        List<ConstellationResponse> constellations = categories.stream()
                .filter(category -> starsByCategory.containsKey(category.getId()))
                .map(category -> new ConstellationResponse(category.getId(), category.getName(),
                        category.getColor(), starsByCategory.get(category.getId())))
                .toList();
        return new UniverseResponse(constellations, unclassified);
    }

    private StarResponse toStar(CoordinateRow row) {
        return new StarResponse(row.itemId(), new double[] {row.x(), row.y(), row.z()},
                row.title(), row.type(), row.url());
    }

    private boolean isEtc(List<Category> categories, Long categoryId) {
        return categories.stream()
                .anyMatch(category -> category.getId().equals(categoryId)
                        && CategoryDefaults.ETC.equals(category.getName()));
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
