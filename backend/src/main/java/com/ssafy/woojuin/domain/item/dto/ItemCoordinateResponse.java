package com.ssafy.woojuin.domain.item.dto;

import java.util.List;

/**
 * 우주(3D) 뷰의 별 하나. 좌표는 워크스페이스 임베딩 전체를 UMAP으로 축소한 값이라
 * 아이템이 추가·삭제되면 <b>전체 배치가 함께 바뀔 수 있다</b> — 프론트는 좌표를 캐시에
 * 오래 두지 말 것.
 *
 * <p>categoryIds는 지도 뷰(ItemGeoResponse)와 같은 용도다 — 별 색(카테고리 색)과
 * 카테고리 필터를 클라이언트가 처리한다.
 */
public record ItemCoordinateResponse(Long itemId, String type, String title,
        List<Long> categoryIds, boolean favorite, double x, double y, double z) {
}
