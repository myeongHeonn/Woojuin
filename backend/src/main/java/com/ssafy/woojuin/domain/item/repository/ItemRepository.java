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

    /** 워크스페이스의 마지막 확인 시각 이후 아이템 활동(생성·처리완료·수정·삭제·즐겨찾기·휴지통) 존재 여부 */
    boolean existsByWorkspaceIdAndUpdatedAtAfter(Long workspaceId, OffsetDateTime updatedAt);

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

    /**
     * 임베딩 백필 대상 워크스페이스 — 임베딩 적격 활성 아이템이 하나라도 있는 곳
     * ({@code ItemEmbeddingBackfillRunner}). 적격 = 요약이 있고(임베딩 입력 계약),
     * 본문·미리보기 설명 중 하나는 있는 것({@code ItemEmbeddingService#hasEmbeddableSourceText}
     * — 텍스트 신호가 제목뿐이면 요약이 지어낸 문장이라 임베딩하지 않는다).
     */
    @Query("select distinct i.workspaceId from Item i where i.deletedAt is null "
            + "and i.summary is not null and trim(i.summary) <> '' "
            + "and (trim(coalesce(i.content, '')) <> '' or trim(coalesce(i.previewDescription, '')) <> '')")
    List<Long> findWorkspaceIdsWithEmbeddableItems();

    /**
     * 임베딩 백필 입력 프로젝션 — 엔티티로 받으면 백필이 쓰지 않는 {@code content}
     * (text 컬럼, 본문 전문)까지 워크스페이스 전건이 메모리로 올라온다({@link #findGeoRows}와
     * 같은 이유). 워크스페이스당 최대 1000건 수준이라 페이지네이션은 두지 않는다.
     * 적격 조건은 {@link #findWorkspaceIdsWithEmbeddableItems}와 같다.
     */
    @Query("select new com.ssafy.woojuin.domain.item.repository.ItemEmbeddingSourceRow("
            + "i.id, i.title, i.summary) "
            + "from Item i "
            + "where i.workspaceId = :workspaceId and i.deletedAt is null "
            + "and i.summary is not null and trim(i.summary) <> '' "
            + "and (trim(coalesce(i.content, '')) <> '' or trim(coalesce(i.previewDescription, '')) <> '')")
    List<ItemEmbeddingSourceRow> findEmbeddingSourceRows(@Param("workspaceId") Long workspaceId);
}
