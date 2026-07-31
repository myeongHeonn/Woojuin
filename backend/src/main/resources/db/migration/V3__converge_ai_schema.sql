-- V3: AI 기능(요약·분류·의미검색·우주 뷰) 스키마를 기존 DB 에 수렴시킨다.
--
-- 왜 필요한가 — **이게 없으면 prod 배포가 기동 실패한다.**
--   AI 기능이 develop 에만 머지된 상태라 환경별로 스키마가 갈려 있다(2026-07-30 실측):
--     dev  : categories.description ○ / vector 확장 ○ / item_embeddings ○
--     prod : 전부 ✗  (main 이 AI 기능 머지 전)
--   `categories.description` 은 **JPA 엔티티 컬럼**이라, `ddl-auto: validate` 아래에서 컬럼이
--   없으면 Hibernate 가 기동을 거부한다. 지금까지는 `update` 가 조용히 붙여줘서 문제가 없었다.
--   Flyway 는 Hibernate 보다 **먼저** 실행되므로 여기서 채우면 validate 가 통과한다.
--
--   V1 이 채워주지 못하는 이유: `baseline-on-migrate` 때문에 **V1 은 기존 DB 에서 실행되지 않는다.**
--   V1 은 빈 DB 용 전체 스키마, V3 는 기존 DB 용 격차 보정 — 둘이 같은 결과에 도달한다.
--
-- 멱등성: 전부 IF (NOT) EXISTS 라 이미 있는 dev 에서는 아무 것도 하지 않고,
--         V1 이 이미 만든 빈 DB 에서도 no-op 이다.

-- ① AI 분류용 카테고리 설명 (JPA 엔티티 컬럼 — validate 대상)
ALTER TABLE categories ADD COLUMN IF NOT EXISTS description character varying(500);

-- ② pgvector 익스텐션. compose 가 postgres 를 `pgvector/pgvector:pg16` 으로 고정한다.
--    옛 `postgres:16-alpine` 컨테이너에서는 여기서 실패한다 — 의도된 fail-fast 다
--    (ItemEmbeddingSchemaInitializer 는 이 실패를 흡수해 "임베딩만 조용히 꺼짐" 이 됐다).
CREATE EXTENSION IF NOT EXISTS vector;

-- ③ 임베딩 저장 테이블. 컬럼 주석과 설계 근거는 V1 참고.
CREATE TABLE IF NOT EXISTS item_embeddings (
    item_id         bigint NOT NULL,
    workspace_id    bigint NOT NULL,
    embedding       vector(1536) NOT NULL,
    embedding_model character varying(100),
    input_hash      character varying(100),
    x               double precision,
    y               double precision,
    z               double precision,
    updated_at      timestamp with time zone NOT NULL DEFAULT now(),

    CONSTRAINT pk_item_embeddings PRIMARY KEY (item_id),
    CONSTRAINT fk_item_embeddings_item FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_item_embeddings_ws ON item_embeddings (workspace_id);

-- ④ item_embeddings 제약조건 이름 통일.
--    dev 는 ItemEmbeddingSchemaInitializer 가 만들어서 Postgres 기본 이름
--    (`item_embeddings_pkey`, `item_embeddings_item_id_fkey`)이 붙어 있다. prod 는 위 ③ 에서
--    규칙 이름으로 새로 만들어진다 → 여기서 양쪽을 같은 이름으로 모은다.
--    (V2 와 같은 이유: 이름이 환경마다 갈리면 나중에 제약조건을 이름으로 다루는
--     마이그레이션이 한쪽에서 반드시 깨진다.)
DO $$
DECLARE
    target record;
    renamed int := 0;
BEGIN
    FOR target IN
        SELECT * FROM (VALUES
            ('item_embeddings', 'item_embeddings_pkey',         'pk_item_embeddings'),
            ('item_embeddings', 'item_embeddings_item_id_fkey', 'fk_item_embeddings_item')
        ) AS t(tbl, old_name, new_name)
    LOOP
        IF EXISTS (
            SELECT 1
            FROM pg_constraint c
            JOIN pg_class      rel ON rel.oid = c.conrelid
            JOIN pg_namespace  ns  ON ns.oid  = rel.relnamespace
            WHERE ns.nspname  = current_schema()
              AND rel.relname = target.tbl
              AND c.conname   = target.old_name
        ) THEN
            EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                           target.tbl, target.old_name, target.new_name);
            renamed := renamed + 1;
        END IF;
    END LOOP;

    RAISE NOTICE 'item_embeddings 제약조건 이름 통일: 변경 %건', renamed;
END $$;
