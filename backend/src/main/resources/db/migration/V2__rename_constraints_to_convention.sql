-- V2: 제약조건 이름을 V1 의 규칙(pk_/uk_/fk_/ck_)으로 통일한다.
--
-- 왜 필요한가
--   V1 은 `baseline-on-migrate` 때문에 **기존 DB(dev/prod/팀원 로컬)에서는 실행되지 않는다.**
--   그래서 기존 DB 의 제약조건 이름은 Hibernate `ddl-auto: update` 가 붙인 그대로 남아 있고,
--   앞으로 새로 만드는 DB 는 V1 의 이름을 갖는다 → **환경마다 이름이 갈린다.**
--
--   이름은 애플리케이션 코드가 참조하지 않지만 **마이그레이션이 참조한다.** 가장 가까운 예:
--   enum 에 값을 추가하면 CHECK 제약을 지우고 다시 만들어야 하는데
--   (`ddl-auto: update` 는 기존 CHECK 를 갱신하지 않아 새 값 저장 시 제약 위반으로 실패한다),
--   그 `DROP CONSTRAINT` 가 환경에 따라 이름이 달라 한쪽에서 반드시 깨진다.
--   그 사고를 미리 없애기 위해 여기서 한 번에 수렴시킨다.
--
-- 안전성
--   - 이름을 **바꾸기만** 한다. 제약조건을 지우거나 다시 만들지 않으므로 데이터 검증이
--     재실행되지 않고 테이블 락도 짧다(카탈로그만 수정).
--   - 대상 이름이 없으면 건너뛴다 → **빈 DB(V1 이 이미 새 이름으로 만든 DB)에서는 no-op.**
--     Postgres 에 `RENAME CONSTRAINT ... IF EXISTS` 가 없어 DO 블록으로 존재 여부를 확인한다.
--   - 여러 번 실행해도 결과가 같다(idempotent). Flyway 가 한 번만 실행하지만,
--     운영 DB 를 손으로 만질 때를 대비해 그렇게 만들어 둔다.
--
-- 참고: PK/UNIQUE 제약을 개명하면 그 제약을 받치는 인덱스 이름도 Postgres 가 함께 바꾼다.
--       `idx_items_ws_active` / `idx_items_ws_geo` 는 원래부터 명시적 이름이라 대상이 아니다.

DO $$
DECLARE
    target record;
    renamed int := 0;
    skipped int := 0;
BEGIN
    FOR target IN
        SELECT * FROM (VALUES
            -- (테이블, 기존 이름, 새 이름)
            -- ── PRIMARY KEY: Postgres 기본 규칙 <table>_pkey
            ('users',                 'users_pkey',                   'pk_users'),
            ('workspaces',            'workspaces_pkey',              'pk_workspaces'),
            ('workspace_members',     'workspace_members_pkey',       'pk_workspace_members'),
            ('workspace_invitations', 'workspace_invitations_pkey',   'pk_workspace_invitations'),
            ('items',                 'items_pkey',                   'pk_items'),
            ('categories',            'categories_pkey',              'pk_categories'),
            ('item_categories',       'item_categories_pkey',         'pk_item_categories'),

            -- ── UNIQUE: Hibernate 가 테이블·컬럼명을 해시해 붙인 이름
            ('users',                 'ukcbysvpk95086ud4n4g6mkspai',  'uk_users_provider'),
            ('workspace_members',     'uk6se2rw5firt04m4vpmqvbnr4u',  'uk_workspace_members_ws_user'),
            ('workspace_invitations', 'uk1t0dwphnnsdo2kem82wfkvlrx',  'uk_workspace_invitations_code'),
            ('categories',            'ukil1jrbsoum178my3lgghpt3be',  'uk_categories_ws_name'),
            ('item_categories',       'uki887ekm6ek0flv2prs8tskqpm',  'uk_item_categories_item_category'),

            -- ── FOREIGN KEY: 같은 해시 규칙
            ('workspaces',            'fklwdvhq4w0563rrp55oy8m0pcb',  'fk_workspaces_created_by'),
            ('workspace_members',     'fk6vtnpc3eexk504u61uepn40p1',  'fk_workspace_members_user'),
            ('workspace_members',     'fkw9hq87n3rvq2c4j47qo78i5r',   'fk_workspace_members_workspace'),
            ('workspace_invitations', 'fk3d59x9m6t1w7tsxbk6bcvrn3q',  'fk_workspace_invitations_created_by'),
            ('workspace_invitations', 'fkcjk1r4awojk9f3vcc7gak8rnv',  'fk_workspace_invitations_workspace'),

            -- ── CHECK: Hibernate 가 @Enumerated(STRING) 에서 만든 규칙적 이름
            ('users',                 'users_avatar_color_check',      'ck_users_avatar_color'),
            ('users',                 'users_provider_check',          'ck_users_provider'),
            ('workspaces',            'workspaces_type_check',         'ck_workspaces_type'),
            ('workspace_members',     'workspace_members_role_check',  'ck_workspace_members_role'),
            ('items',                 'items_status_check',            'ck_items_status'),
            ('items',                 'items_type_check',              'ck_items_type')
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
        ELSE
            skipped := skipped + 1;
        END IF;
    END LOOP;

    RAISE NOTICE '제약조건 이름 통일: 변경 %건 / 건너뜀 %건 (건너뜀 = 이미 새 이름)', renamed, skipped;
END $$;
