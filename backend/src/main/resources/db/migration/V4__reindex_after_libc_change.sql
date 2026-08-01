-- V4: postgres 이미지 교체(musl → glibc)로 무효가 된 문자열 인덱스를 재생성한다.
--
-- 배경
--   pgvector 도입으로 compose 의 postgres 이미지가 바뀌었다:
--     postgres:16-alpine  (musl libc)  →  pgvector/pgvector:pg16  (debian, glibc)
--   같은 PG16 이라 데이터 볼륨은 그대로 호환되지만, **두 libc 의 문자열 정렬(collation) 구현이
--   다르다.** btree 인덱스는 만들 때의 정렬 순서를 전제로 저장되므로, libc 가 바뀌면
--   **text/varchar 컬럼 인덱스가 조용히 무효**가 된다 — 검색이 있는 행을 놓치고, 무엇보다
--   **UNIQUE 제약이 중복을 못 잡는다.** 오류 없이 잘못된 결과를 내는 유형이라 발견이 어렵다.
--   (팀원도 compose 주석에 "기존 볼륨을 이 이미지로 처음 올릴 땐 REINDEX 가 필수" 라고 남겼다.)
--
-- 왜 마이그레이션으로 두는가
--   원래 성격은 스키마 변경이 아니라 일회성 운영 조치다. 그래도 여기 두는 이유는
--   **환경마다 사람이 기억해서 해야 하는 절차는 반드시 빠진다**는 것이고, 빠졌을 때의 결과가
--   조용한 데이터 무결성 훼손이라서다. Flyway 에 두면 모든 환경에서 정확히 한 번 보장된다.
--   (2026-07-30 dev 는 손으로 `REINDEX DATABASE` 를 이미 실행했다 — 중복 실행은 무해하다.)
--
-- 대상 선정
--   collation 에 의존하는 인덱스, 즉 **text/varchar 컬럼이 들어간 인덱스**만 대상이다:
--     users                 — UNIQUE (provider, provider_id)
--     workspace_invitations — UNIQUE (code)
--     categories            — UNIQUE (workspace_id, name)
--   나머지는 영향이 없다: items 의 두 부분 인덱스는 (bigint, timestamptz) 이고,
--   workspaces / workspace_members / item_categories / item_embeddings 의 인덱스는 bigint 뿐이다.
--   정수·시각·vector 타입은 정렬이 libc 와 무관하다.
--
-- 주의
--   `REINDEX DATABASE` 는 트랜잭션 안에서 실행할 수 없어 쓰지 않는다(Flyway 는 마이그레이션을
--   트랜잭션으로 감싼다). `REINDEX TABLE` 은 가능하다. 테이블마다 ACCESS EXCLUSIVE 락을 잡지만
--   현재 데이터 규모에서는 즉시 끝난다. 데이터가 커진 뒤 같은 상황이 오면
--   `REINDEX INDEX CONCURRENTLY` 를 검토할 것.

REINDEX TABLE users;
REINDEX TABLE workspace_invitations;
REINDEX TABLE categories;
