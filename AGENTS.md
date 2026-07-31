# AGENTS.md — 우주인 (Woojuin)

AI 코딩 에이전트(Claude Code, Codex, Cursor, Gemini CLI 등)를 위한 프로젝트 가이드.

## 프로젝트 개요

**우주인**: 흩어진 링크·사진·메모를 한 곳에 모아 AI가 자동 분류·요약·정리해주는 올인원 AI 스크랩북.
핵심 가치: **"저장은 1초, 정리는 AI가, 찾을 땐 검색 한 번."**

SSAFY 공통프로젝트, 6인 팀, 실질 개발 기간 3주. 빠른 구현과 단순한 구조를 우선한다.

## 레포 구조 (모노레포)

```
frontend/    # 웹앱 — React + Vite + TS, PWA. 사용자용 메인 서비스
extension/   # 크롬 익스텐션 — React + Vite, Manifest V3
backend/     # API 서버 — Spring Boot, Java 21
wearos/      # Wear OS 앱 (Kotlin) — 스트레치 목표, 현재 README만 존재
docs/        # 컨벤션 문서
```

작업 요청이 어느 파트인지 확인하고 **해당 폴더만** 수정할 것. 여러 파트에 걸친 변경(예: API 스펙 변경)은 명시적으로 언급할 것.

## 기술 스택 (변경 금지 사항 포함)

| 영역 | 선택 | 주의 |
| --- | --- | --- |
| 웹앱 | React 18 + Vite + TypeScript | **Next.js/SSR 도입 금지** (팀 결정 완료) |
| PWA | vite-plugin-pwa, injectManifest 전략 | 서비스워커는 `frontend/src/sw.ts` |
| 서버 상태 | TanStack Query | 저장 상태(PROCESSING→DONE) 폴링에 사용 |
| 클라이언트 상태 | **Jotai** | zustand 아님. localStorage 직접 사용 금지 |
| 백엔드 | Spring Boot 3.3, Java 21 | |
| DB | PostgreSQL | 검색은 FTS(tsvector), Elasticsearch 금지(스트레치) |
| 큐 | Redis Streams | 스트림 키: `woojuin:item-processing` |
| AI | OpenAI API (gpt-4o-mini) | |
| 지도 | **OpenFreeMap + MapLibre GL** | 카카오맵/구글맵 SDK 아님. 타일 호스팅 무료·API 키 없음. 지도 로직은 어댑터(`frontend/src/components/domain/map/mapAdapter.ts`)로 추상화, 좌표는 lat/lng 원시값으로 다룸 |
| 지오코딩 | **카카오 로컬 REST API** | 주소↔좌표 변환 전용(서버측 REST, 지도 SDK 아님). `Geocoder` 인터페이스 뒤에 두고 `KAKAO_REST_API_KEY` 없으면 NoOp으로 폴백 — 키 없이도 앱이 뜨고 지도 링크·EXIF 좌표는 저장된다 |
| 인증 | JWT + OAuth(카카오/구글) + 이메일/비밀번호 | 일반 가입은 6자리 이메일 인증코드 필수 |
| CI/CD | **GitLab CI** (.gitlab-ci.yml) | GitHub Actions 아님 |

## 명령어 (작업 후 반드시 검증)

```bash
# 로컬 인프라 (PostgreSQL + Redis)
docker compose up -d

# frontend / extension
cd frontend && npm run lint && npm run type-check && npm run build
cd extension && npm run build

# backend
cd backend && ./gradlew test
cd backend && ./gradlew bootRun   # 로컬 실행 (:8080)
```

프론트 코드를 수정했으면 최소 `lint` + `type-check`는 통과시킨 후 완료로 보고할 것.

## 코드 컨벤션

- 브랜치/커밋/MR 규칙: `docs/CONVENTIONS.md` 참조 (Conventional Commits, `feature/FE-{설명}` 형식)
- **API 공통 응답**: 모든 엔드포인트는 `{ "status": 200, "message": "success", "data": {} }` 형식. 백엔드에서 `com.ssafy.woojuin.global.common.ApiResponse` 레코드 사용
- 프론트 import는 절대경로 `@/` 사용 (`@/shared/api/client` 등)
- 백엔드 패키지: `global/`(공통 설정·에러·응답) + `domain/{auth,workspace,item,ai,notification}/`(도메인별 컨트롤러·서비스·리포지토리·엔티티)
- 시크릿 커밋 금지. 환경변수 키 이름만 `.env.example`에 추가, 실제 값은 팀 채널로 공유

## 도메인 핵심 개념 (코드만 봐서는 모르는 것)

1. **"저장은 1초" 원칙**: 저장 API는 즉시 201 응답(`status: PROCESSING`)하고, AI 가공(요약·태그·OCR·좌표변환)은 Redis 큐를 통해 백그라운드 워커가 처리. 저장 경로에 동기 AI 호출을 넣지 말 것
2. **ItemStatus 4단계**: `PROCESSING → DONE | PARTIAL | FAILED`. PARTIAL은 미리보기(트랙 A)는 성공했지만 콘텐츠 확보(트랙 B)가 실패한 상태
3. **트랙 A / 트랙 B**: 모든 콘텐츠 처리는 두 독립 트랙으로 나뉨 — A는 미리보기 카드 생성(oEmbed/OG, 거의 항상 성공), B는 AI가 요약·분류할 실제 콘텐츠 확보(실패 가능). 하나의 실패가 다른 쪽에 영향을 주면 안 됨
4. **삭제는 soft delete 우선**: 사용자/AI 서포터의 삭제 액션은 항상 휴지통 이동(`items.deleted_at`)이 먼저. 영구 삭제는 휴지통에서만 가능
5. **워크스페이스 권한**: 모든 아이템은 워크스페이스에 속하며, 접근 시 `WorkspaceMember`(OWNER/MEMBER) 검증 필요
6. **알림 정책**: AI 처리 DONE 또는 PARTIAL이면 푸시 발송, FAILED만 발송 생략. 알림 거부 유저를 위해 상태 조회 API(`GET /items/{id}/status`)가 폴백

## 문서

- 상세 기획서·요구사항 명세서(FR/NFR)·API 명세서·ERD는 팀 노션에 있음
- 노션과 코드가 충돌하면 **코드에 반영된 최신 결정이 우선**, 단 그 사실을 사용자에게 알릴 것
- 알려진 문서 불일치: FR-026(유튜브 자막 API)은 폐기 방향 — 실제 구현은 oEmbed 통합 방식
- 알려진 문서 불일치: ERD 설계 노트의 `ai_results` 테이블은 **만들어진 적이 없다**. AI 산출물(`summary`)과 지도 좌표(`lat`/`lng`/`address`)는 모두 `items`에 있고, `search_vector`(GENERATED + GIN)도 없다 — 통합 검색은 `items`의 텍스트 컬럼을 ILIKE로 훑는다. 노션 테이블 명세에 정정 표기를 남겨 뒀다

## 하지 말 것

- Next.js, Elasticsearch, 카카오맵/구글맵 지도 SDK 등 이미 배제된 기술 도입 (지도는 OpenFreeMap + MapLibre 확정. 카카오는 **로컬 REST API(주소↔좌표)만** 쓰고 지도 SDK는 쓰지 않는다)
- `items`에 GENERATED 컬럼 추가 — `ddl-auto=update`가 매 기동마다 text 컬럼 타입 변경을 재시도하는데 PostgreSQL이 생성 컬럼 참조 컬럼의 타입 변경을 거부해 **두 번째 기동부터 앱이 뜨지 않는다**. 필요하면 표현식 인덱스로 (`ItemIndexInitializer` javadoc 참고)
- `main`/`develop`에 직접 푸시하는 워크플로우 가정 (MR 기반)
- 저장 API에 동기 AI 호출 추가
- 프론트에서 localStorage/sessionStorage 직접 사용 (Jotai/TanStack Query로 대체)
- `.env`, 시크릿, `firebase-adminsdk*.json` 등을 커밋하는 코드/설정 작성
- 커밋 메시지에 `Co-Authored-By: Claude` 등 AI 툴 서명 남기기
