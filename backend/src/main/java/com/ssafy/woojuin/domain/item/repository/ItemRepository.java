package com.ssafy.woojuin.domain.item.repository;

import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.global.common.ItemStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {

    long countByCreatedByAndDeletedAtIsNull(Long createdBy);

    long countByCreatedByAndDeletedAtIsNullAndCreatedAtGreaterThanEqual(
            Long createdBy, OffsetDateTime createdAt);

    /**
     * 오래 PROCESSING에 머문 아이템 조회 — 재발행 안전망({@code StuckItemRepublisher})용.
     * 오래된 것부터 처리하도록 createdAt 오름차순. 휴지통 여부는 거르지 않는다 —
     * 휴지통 아이템도 가공해 두면 복구했을 때 결과가 그대로 유효하다.
     */
    List<Item> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            ItemStatus status, OffsetDateTime cutoff, Limit limit);

    /**
     * 지도 뷰용 좌표 보유 아이템 조회 (FR-032).
     *
     * <p>엔티티가 아니라 프로젝션으로 받는다. 지도 조회는 페이지네이션이 없어 워크스페이스의
     * 좌표 있는 아이템을 <b>전부</b> 한 번에 읽는데, 엔티티로 받으면 지도가 쓰지도 않는
     * {@code content}(text 컬럼, 본문 전문)와 {@code summary}까지 모두 메모리와 영속성
     * 컨텍스트로 올라온다.
     *
     * <p>{@code Specification}을 쓰지 않는 이유는 동적 파라미터가 하나도 없기 때문이다
     * (목록 조회와 달리 필터가 없다).
     *
     * <p>WHERE 조건은 {@code idx_items_ws_geo} 부분 인덱스의 조건과 정확히 일치시켰다
     * ({@code db/migration/V1__init.sql} 참고). 조건이 갈리면 인덱스를 못 탄다.
     *
     * <p>정렬하지 않는다 — 지도는 핀을 좌표로 배치하므로 순서에 의미가 없다.
     */
    @Query("select new com.ssafy.woojuin.domain.item.repository.ItemGeoRow("
            + "i.id, i.type, i.title, i.favorite, i.lat, i.lng, i.address) "
            + "from Item i "
            + "where i.workspaceId = :workspaceId and i.deletedAt is null "
            + "and i.lat is not null and i.lng is not null")
    List<ItemGeoRow> findGeoRows(@Param("workspaceId") Long workspaceId, Limit limit);
}
