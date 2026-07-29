package com.ssafy.woojuin.domain.item.repository;

import com.ssafy.woojuin.domain.ai.AiMixClient.ItemVector;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * item_embeddings 접근. JPA를 안 쓰는 이유는 {@code ItemEmbeddingSchemaInitializer} javadoc 참고
 * — vector 타입을 ddl-auto 밖에 두려는 결정이고, 접근 패턴도 전부 벌크/upsert라 JPA 이점이 없다.
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

    /** 재계산된 좌표 일괄 반영. */
    public void updateCoordinates(List<Object[]> xyzByItemId) {
        jdbcTemplate.batchUpdate(
                "UPDATE item_embeddings SET x = ?, y = ?, z = ?, updated_at = now() WHERE item_id = ?",
                xyzByItemId);
    }

    /** 우주 뷰 조회용 행. 카테고리 연결은 서비스가 별도 조회로 붙인다(지도 뷰와 동일 방식). */
    public record CoordinateRow(Long itemId, String type, String title, String url,
            double x, double y, double z) {
    }

    /** 좌표가 계산된 활성 아이템의 우주 뷰 행들. url은 URL 타입 별의 바로 이동용. */
    public List<CoordinateRow> findCoordinates(Long workspaceId) {
        return jdbcTemplate.query(
                "SELECT e.item_id, i.type, i.title, i.url, e.x, e.y, e.z "
                        + "FROM item_embeddings e "
                        + "JOIN items i ON i.id = e.item_id AND i.deleted_at IS NULL "
                        + "WHERE e.workspace_id = ? AND e.x IS NOT NULL "
                        + "ORDER BY e.item_id",
                (rs, i) -> new CoordinateRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getDouble(5), rs.getDouble(6), rs.getDouble(7)),
                workspaceId);
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
