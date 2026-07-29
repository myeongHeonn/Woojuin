package com.ssafy.woojuin.domain.item.dto;

import java.util.List;

/**
 * 우주(3D) 뷰 응답. 프론트 씬 코드가 소비하는 계약과 필드까지 맞춘다
 * (frontend/src/stores/mock/universe.ts의 UniverseResponse — 별자리 위치·크기는
 * 클라이언트가 소속 아이템 좌표에서 파생하므로 서버는 주지 않는다).
 *
 * <p>좌표는 워크스페이스 임베딩 전체를 UMAP으로 축소한 값이라 아이템이 추가·삭제되면
 * <b>전체 배치가 함께 바뀔 수 있다</b> — 프론트는 오래 캐시하지 말 것.
 *
 * @param constellations 카테고리(별자리)별 별 묶음. 여러 카테고리에 속한 아이템은 각
 *                       별자리에 <b>중복으로</b> 들어간다(프론트 목업과 동일 규칙)
 * @param unclassified   "기타"에만 속한 별 — 별자리 없이 홀로 뜬다. 기타의 미분류 색
 *                       (#F5F1E8)이 프론트 UNCLASSIFIED_COLOR와 일치하는 게 이 매핑의 근거다
 */
public record UniverseResponse(List<ConstellationResponse> constellations,
        List<StarResponse> unclassified) {

    /**
     * @param color 카테고리 색 hex 문자열(예 "#C9B8FF") — 카테고리 API(CategoryResponse)와
     *              동일 형식. 목업은 숫자(0xc9b8ff)지만 백엔드 응답끼리 형식을 통일한다
     */
    public record ConstellationResponse(Long categoryId, String categoryName, String color,
            List<StarResponse> items) {
    }

    /**
     * @param position [x, y, z]
     *
     * <p>url은 의도적으로 없다 — 별 클릭 동작을 타입과 무관하게 상세 모달로 통일했다
     * (URL 별만 새 탭으로 튀면 동작이 갈라진다). 원본 링크는 상세 조회 응답에 있다.
     */
    public record StarResponse(Long id, double[] position, String title, String type) {
    }
}
