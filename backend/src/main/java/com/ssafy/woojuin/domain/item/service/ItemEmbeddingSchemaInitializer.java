package com.ssafy.woojuin.domain.item.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 임베딩 저장 테이블(item_embeddings)과 pgvector 익스텐션을 기동 시 한 번 준비한다.
 *
 * <p>JPA 엔티티로 만들지 않고 순수 SQL로 관리하는 이유: vector(1536) 타입을 ddl-auto=update
 * 아래 두면 Hibernate가 매 기동마다 타입을 재검사하는 경로에 들어간다 — items의 GENERATED
 * 컬럼 함정(ItemIndexInitializer javadoc)과 같은 계열의 위험이다. 이 테이블은 접근 패턴이
 * 전부 단건 upsert/워크스페이스 벌크 조회라 JPA의 이점도 없고, 이후 의미 기반 검색(pgvector
 * 연산자 {@code <=>})도 어차피 네이티브 SQL이다.
 *
 * <p>CREATE EXTENSION은 pgvector 번들 이미지(docker-compose의 pgvector/pgvector:pg16)를
 * 전제한다. 옛 postgres:16-alpine 볼륨으로 돌리면 여기서 실패하는데, 기동은 계속하고
 * 임베딩 저장만 건별로 실패(흡수)한다 — 저장·검색·요약은 정상 동작한다.
 *
 * <p>모든 DDL이 IF NOT EXISTS라 재기동해도 안전하다(idempotent).
 */
@Slf4j
@Component
public class ItemEmbeddingSchemaInitializer {

    private static final List<String> STATEMENTS = List.of(
            "CREATE EXTENSION IF NOT EXISTS vector",

            // item_id가 PK — 아이템당 임베딩 하나. 영구 삭제 시 함께 지워지도록 FK CASCADE.
            // (휴지통 soft delete는 행을 남기고, 조회 쪽에서 items.deleted_at으로 거른다)
            // workspace_id를 중복 보관하는 건 재계산·좌표 조회가 워크스페이스 단위 벌크라
            // 매번 items를 조인해 거를 필요를 줄이기 위해서다.
            // x/y/z가 nullable인 건 "임베딩은 저장됐지만 아직 좌표 재계산 전"인 순간이 있어서다.
            "CREATE TABLE IF NOT EXISTS item_embeddings ("
                    + "item_id BIGINT PRIMARY KEY REFERENCES items(id) ON DELETE CASCADE, "
                    + "workspace_id BIGINT NOT NULL, "
                    + "embedding vector(1536) NOT NULL, "
                    + "embedding_model VARCHAR(100), "
                    + "input_hash VARCHAR(100), "
                    + "x DOUBLE PRECISION, y DOUBLE PRECISION, z DOUBLE PRECISION, "
                    + "updated_at TIMESTAMPTZ NOT NULL DEFAULT now())",

            "CREATE INDEX IF NOT EXISTS idx_item_embeddings_ws ON item_embeddings (workspace_id)");

    private final JdbcTemplate jdbcTemplate;

    public ItemEmbeddingSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        for (String sql : STATEMENTS) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.error("임베딩 스키마 준비 실패 — 임베딩 저장만 꺼지고 나머지는 정상 동작. "
                        + "postgres 이미지가 pgvector/pgvector:pg16인지 확인할 것. sql={}", sql, e);
                return;
            }
        }
        log.info("임베딩 스키마 준비 완료 (pgvector + item_embeddings)");
    }
}
