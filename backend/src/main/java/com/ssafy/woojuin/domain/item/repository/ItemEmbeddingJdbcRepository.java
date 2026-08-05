package com.ssafy.woojuin.domain.item.repository;

import com.ssafy.woojuin.domain.ai.AiMixClient.ItemVector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * item_embeddings 접근. 이 테이블을 JPA 엔티티로 만들지 않은 이유는
 * {@code db/migration/V1__init.sql} 의 해당 절 주석 참고 — vector 타입을 ddl-auto(validate)
 * 밖에 두려는 결정이고, 접근 패턴도 전부 벌크/upsert라 JPA 이점이 없다.
 *
 * <p>vector 값은 pgvector의 문자열 리터럴({@code '[0.1,0.2,...]'})로 주고받는다 — JDBC
 * 드라이버에 별도 타입 등록 없이 {@code ?::vector} 캐스팅으로 충분하다.
 */
@Repository
public class ItemEmbeddingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public ItemEmbeddingJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 저장된 입력 해시. 입력(제목·요약·카테고리)이 안 바뀐 재임베딩을 건너뛰는 근거. */
    public Optional<String> findInputHash(Long itemId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT input_hash FROM item_embeddings WHERE item_id = ?",
                (rs, i) -> rs.getString(1), itemId);
        return rows.stream().findFirst();
    }

    /** 여러 아이템의 저장된 입력 해시 — 백필이 무변경 업서트를 건너뛰는 근거(멱등 재실행). */
    public Map<Long, String> findInputHashes(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(itemIds.size(), "?"));
        Map<Long, String> hashes = new HashMap<>();
        jdbcTemplate.query(
                "SELECT item_id, input_hash FROM item_embeddings WHERE item_id IN (" + placeholders + ")",
                rs -> {
                    hashes.put(rs.getLong(1), rs.getString(2));
                },
                itemIds.toArray());
        return hashes;
    }

    /** 임베딩 upsert. 좌표(x/y/z)는 건드리지 않는다 — 재계산이 별도로 채운다. */
    public void upsert(Long itemId, Long workspaceId, float[] embedding, String model, String inputHash) {
        jdbcTemplate.update(
                "INSERT INTO item_embeddings (item_id, workspace_id, embedding, embedding_model, input_hash) "
                        + "VALUES (?, ?, ?::vector, ?, ?) "
                        + "ON CONFLICT (item_id) DO UPDATE SET "
                        + "embedding = EXCLUDED.embedding, embedding_model = EXCLUDED.embedding_model, "
                        + "input_hash = EXCLUDED.input_hash, updated_at = now()",
                itemId, workspaceId, toVectorLiteral(embedding), model, inputHash);
    }

    /**
     * 좌표 재계산 입력 — 워크스페이스의 활성(휴지통 제외) 아이템 임베딩 전부.
     * 휴지통 아이템은 우주에서 빠져야 하므로 items를 조인해 거른다.
     */
    public List<ItemVector> findActiveVectors(Long workspaceId) {
        return jdbcTemplate.query(
                "SELECT e.item_id, e.embedding::text FROM item_embeddings e "
                        + "JOIN items i ON i.id = e.item_id AND i.deleted_at IS NULL "
                        + "WHERE e.workspace_id = ?",
                (rs, i) -> new ItemVector(rs.getLong(1), parseVectorLiteral(rs.getString(2))),
                workspaceId);
    }

    /**
     * 텍스트 신호가 제목뿐인 아이템(본문·미리보기 설명 모두 빈 것)의 임베딩 제거 —
     * 적격 규칙({@code ItemEmbeddingService#hasEmbeddableSourceText}) 도입 전에 저장된
     * 무의미한 벡터를 백필이 한 번 청소한다. 규칙 도입 후에는 애초에 저장되지 않는다.
     * 휴지통 아이템도 지운다 — 복구돼도 어차피 부적격이다.
     *
     * @return 지운 행 수
     */
    public int deleteEmbeddingsWithoutSourceText() {
        return jdbcTemplate.update(
                "DELETE FROM item_embeddings e USING items i WHERE i.id = e.item_id "
                        + "AND trim(coalesce(i.content, '')) = '' "
                        + "AND trim(coalesce(i.preview_description, '')) = ''");
    }

    /** 재계산된 좌표 일괄 반영. */
    public void updateCoordinates(List<Object[]> xyzByItemId) {
        jdbcTemplate.batchUpdate(
                "UPDATE item_embeddings SET x = ?, y = ?, z = ?, updated_at = now() WHERE item_id = ?",
                xyzByItemId);
    }

    /** 우주 뷰 조회용 행. 카테고리 연결은 서비스가 별도 조회로 붙인다(지도 뷰와 동일 방식). */
    public record CoordinateRow(Long itemId, String type, String title,
            double x, double y, double z) {
    }

    /** 좌표가 계산된 활성 아이템의 우주 뷰 행들. */
    public List<CoordinateRow> findCoordinates(Long workspaceId) {
        return jdbcTemplate.query(
                "SELECT e.item_id, i.type, i.title, e.x, e.y, e.z "
                        + "FROM item_embeddings e "
                        + "JOIN items i ON i.id = e.item_id AND i.deleted_at IS NULL "
                        + "WHERE e.workspace_id = ? AND e.x IS NOT NULL "
                        + "ORDER BY e.item_id",
                (rs, i) -> new CoordinateRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getDouble(4), rs.getDouble(5), rs.getDouble(6)),
                workspaceId);
    }

    /** 의미 검색 결과 행. distance는 코사인 거리(0=동일, 2=정반대) — 임계값 튜닝 로그에도 쓴다. */
    public record SimilarityRow(Long itemId, double distance) {
    }

    /**
     * 임계값 안에 드는 활성 아이템 수 — 의미 검색 페이지네이션의 totalElements.
     *
     * @param excludeItemIds 제외할 아이템(보충 검색에서 키워드 결과와의 중복 제거).
     *                       빈 목록이면 조건 자체가 붙지 않는다
     */
    public long countSimilar(Long workspaceId, float[] queryEmbedding, double maxDistance,
            Collection<Long> excludeItemIds) {
        List<Object> params = new ArrayList<>(
                List.of(workspaceId, toVectorLiteral(queryEmbedding), maxDistance));
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM item_embeddings e "
                        + "JOIN items i ON i.id = e.item_id AND i.deleted_at IS NULL "
                        + "WHERE e.workspace_id = ? AND (e.embedding <=> ?::vector) < ?"
                        + notInClause(excludeItemIds, params),
                Long.class, params.toArray());
        return count == null ? 0 : count;
    }

    /**
     * 검색어 벡터와 가까운 순으로 활성 아이템을 찾는다({@code <=>} = 코사인 거리). 임계값이
     * 없으면 아무리 무관한 검색어여도 "가장 덜 먼" 아이템이 나오므로 반드시 거리로 자른다.
     *
     * <p>인덱스(HNSW)는 일부러 안 만든다 — 워크스페이스당 최대 1000건 수준이라 정확 스캔이
     * 이미 빠르고, HNSW는 근사 탐색이라 workspace_id 필터와 결합하면 결과가 누락될 수 있다.
     * 키워드 검색이 GIN을 버린 것({@link ItemSearchRepository})과 같은 계열의 결정이다.
     *
     * @param excludeItemIds 제외할 아이템(보충 검색에서 키워드 결과와의 중복 제거).
     *                       빈 목록이면 조건 자체가 붙지 않는다
     */
    public List<SimilarityRow> searchBySimilarity(Long workspaceId, float[] queryEmbedding,
            double maxDistance, Collection<Long> excludeItemIds, int limit, int offset) {
        String vector = toVectorLiteral(queryEmbedding);
        List<Object> params = new ArrayList<>(List.of(vector, workspaceId, vector, maxDistance));
        String exclusion = notInClause(excludeItemIds, params);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(
                "SELECT e.item_id, e.embedding <=> ?::vector AS distance FROM item_embeddings e "
                        + "JOIN items i ON i.id = e.item_id AND i.deleted_at IS NULL "
                        + "WHERE e.workspace_id = ? AND (e.embedding <=> ?::vector) < ?"
                        + exclusion
                        + " ORDER BY distance, i.created_at DESC, e.item_id LIMIT ? OFFSET ?",
                (rs, i) -> new SimilarityRow(rs.getLong(1), rs.getDouble(2)),
                params.toArray());
    }

    /** NOT IN 절을 조립하고 params에 id들을 순서대로 붙인다. 제외가 없으면 빈 문자열. */
    private static String notInClause(Collection<Long> excludeItemIds, List<Object> params) {
        if (excludeItemIds.isEmpty()) {
            return "";
        }
        params.addAll(excludeItemIds);
        return " AND e.item_id NOT IN ("
                + String.join(",", Collections.nCopies(excludeItemIds.size(), "?")) + ")";
    }

    /** pgvector 리터럴로 직렬화: [0.1,0.2,...] */
    static String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder(embedding.length * 12).append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        return builder.append(']').toString();
    }

    /** pgvector 리터럴 파싱: [0.1,0.2,...] → float[] */
    static float[] parseVectorLiteral(String literal) {
        String body = literal.substring(1, literal.length() - 1);
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] values = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Float.parseFloat(parts[i].trim());
        }
        return values;
    }
}
