package com.ssafy.woojuin.domain.item.dto;

import com.ssafy.woojuin.domain.item.entity.ItemType;
import java.util.List;

/**
 * 지도 뷰 핀 하나 (FR-032). {@code GET /api/workspaces/{workspaceId}/items/geo} 응답 원소.
 *
 * <p>{@link ItemSummaryResponse}를 재사용하지 않는 이유가 둘이다. 카테고리 형태가 다르고
 * (평면 {@code categoryIds} 배열), 목록 응답이 끌고 오는 {@code summary}·{@code preview}·
 * {@code imageUrl}(IMAGE마다 S3 presign 발생)이 지도엔 전부 불필요하다.
 *
 * <p>카테고리를 {@code {categoryId, name, color}} 객체가 아니라 id 배열로 내리는 이유:
 * 프론트는 핀 색과 필터 칩을 그리려고 카테고리 목록 API를 이미 받아둔 상태이므로 id만으로
 * 조립할 수 있고, 핀마다 이름·색을 중복 전송하면 페이로드만 커진다.
 *
 * <p>{@code favorite}은 지도의 "즐겨찾기만 보기" 토글이 쓴다. 클라이언트가 이미 받은 핀
 * 목록에서 바로 필터링하므로 서버에 favorite 쿼리 파라미터를 두지 않았다.
 *
 * <p>{@code title}과 {@code address}는 <b>null일 수 있다</b>. title은 저장 시점에 비어 있고
 * 미리보기가 실패하면 채워지지 않으며, address는 지도 공유 링크에서 좌표만 얻었거나
 * 역지오코딩이 실패한 경우 비어 있다(핀이 목적이고 주소는 장식이다).
 */
public record ItemGeoResponse(
        Long itemId,
        ItemType type,
        String title,
        List<Long> categoryIds,
        boolean favorite,
        Double lat,
        Double lng,
        String address) {
}
