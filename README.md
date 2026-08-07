<!--
  표지 이미지 — docs/images/cover.png 파일을 팀 표지로 **덮어쓰면** 됩니다(권장 16:9).
  파일명을 바꾸고 싶으면 아래 src 도 같이 바꿔주세요.
-->
<div align="center">
  <img src="docs/images/cover.png" alt="우주인 (Woojuin)" width="100%" />
</div>

<div align="center">

# 🪐 우주인 (Woojuin)

### 우리 주변의 인포메이션

**저장은 1초, 정리는 AI가, 찾을 땐 검색 한 번.**

흩어진 링크·사진·메모를 한곳에 모으면, AI가 제목·요약·카테고리를 붙여 정리하고<br />
서로 관련 있는 정보를 **별자리처럼 이어 보여주는** 올인원 AI 스크랩북

[**woojuin.store**](https://woojuin.store) · [**시연 시나리오 (Figma)**](https://www.figma.com/design/zWIwdtjk1xYT0fwPjdrkvF/Scenario?node-id=258-2468)

SSAFY 15기 광주1반 공통프로젝트 (6인 / 2026.07.13 ~ 2026.08.07)

</div>

---

## 🌌 이런 문제를 풉니다

> 나중에 볼 링크는 카톡 '나에게 보내기'에, 가고 싶은 식당은 스크린샷으로, 떠오른 생각은 메모 앱에.
> 저장은 했는데 **어디에 뒀는지 몰라서 결국 다시 찾지 못하는** 정보들.

우주인은 저장하는 순간의 부담을 없애고(형식도, 폴더도 고르지 않습니다) 정리는 AI에게 맡깁니다.
쌓인 정보는 목록이 아니라 **의미가 가까운 것끼리 모인 3D 우주**로 보여줘서, 이름이 기억나지 않아도 눈으로 되찾을 수 있습니다.

---

## ✨ 주요 기능

### 1. 어디에서든 1초 만에 저장

떠오른 순간이 곧 저장 순간이 되도록, 쓰던 화면을 벗어나지 않고 보낼 수 있는 통로를 넓게 열어 뒀습니다.

| 통로                        | 이렇게 저장합니다                                                    |
| --------------------------- | -------------------------------------------------------------------- |
| 🌐 **웹앱**                 | 링크·사진·메모를 붙여 넣기 한 번으로                                 |
| 🧩 **Chrome 확장**          | 보고 있는 페이지를 툴바 버튼 / 우클릭 메뉴로 바로                    |
| 📱 **모바일 공유**          | PWA 설치 후 안드로이드 공유 시트에서 "우주인에게 보내기"             |
| 💬 **Discord · Mattermost** | 채널에서 슬래시 명령으로 저장하고, 채널↔워크스페이스 매핑까지        |
| ⌚ **Wear OS**              | 갤럭시 워치에서 지금 있는 장소·음성 메모·듣고 있는 노래를 손목만으로 |

저장 API는 **즉시 응답**하고(`PROCESSING`), 크롤링·AI 가공은 뒤에서 처리합니다. 기다림 없이 하던 일을 계속하면 됩니다.

### 2. 정리는 AI가

저장된 콘텐츠는 백그라운드 워커가 종류별로 다르게 가공합니다.

- **URL** — 미리보기(oEmbed·OG) 카드 생성 + 본문 추출 → AI 제목·요약. JS 렌더링 SPA나 봇 차단 페이지는 스텔스 브라우저 크롤러가 한 번 더 시도합니다
- **사진** — 비전 모델이 이미지를 설명하고 안의 글자(OCR)를 읽어 요약. EXIF의 GPS 좌표는 지도에 꽂힙니다
- **메모** — 긴 글도 제목과 한 줄 요약으로

그리고 모든 아이템은 워크스페이스에 있는 **카테고리 중에서만** 자동 분류됩니다(기본 11종 제공, 직접 추가·수정·삭제 가능). AI가 멋대로 새 카테고리를 만들지 않아서 목록이 어질러지지 않습니다.

> 처리는 **미리보기 트랙**과 **콘텐츠 트랙**으로 나뉘어 돌아갑니다. 크롤링이 막혀도 미리보기 카드는 남기 때문에, 저장한 것이 통째로 사라지는 일은 없습니다(`PARTIAL`).

### 3. 세 가지 시선으로 다시 보기

같은 워크스페이스를 목적에 따라 다르게 봅니다.

| 뷰                | 무엇을 보여주나                                                                              |
| ----------------- | -------------------------------------------------------------------------------------------- |
| 🌟 **성좌**       | 의미가 비슷한 아이템이 가까이 놓인 3D 우주. 임베딩을 UMAP으로 3차원까지 줄여 좌표를 만듭니다 |
| 📚 **라이브러리** | 카테고리 칩으로 걸러 보는 카드 목록                                                          |
| 🗺 **지도**       | 좌표가 있는 링크·사진을 지도 위에. 주변 장소 검색으로 "그 근처 뭐 저장했더라"까지            |

### 4. 이름이 기억나지 않아도 찾기

- **키워드 검색** — 제목·요약·본문·카테고리를 한 번에
- **AI 검색** — 토글을 켜면 `"저번에 저장한 그 파스타집 어디였지?"` 같은 문장을 그대로 받습니다. 키워드로 걸리지 않으면 **의미(임베딩) 기반 검색**으로 넘어가서, 단어가 하나도 겹치지 않아도 뜻이 가까운 아이템을 찾아냅니다

### 5. 혼자서도, 여럿이서도

- **개인 스페이스** — 가입하면 바로 생기는 나만의 우주
- **워크스페이스** — 초대 링크로 함께 모으는 공간. OWNER/MEMBER 권한, 멤버 활동 표시, 누가 무엇을 새로 담았는지 확인
- **실시간 반영** — 다른 사람이 저장하거나 AI 처리가 끝나면 SSE로 화면이 스스로 갱신됩니다
- **웹 푸시** — 앱을 꺼 뒀어도 처리 완료 알림이 옵니다
- **휴지통** — 삭제는 언제나 휴지통이 먼저. 영구 삭제는 휴지통 안에서만

---

## 🏗 서비스 구조

```
 [웹앱 PWA]  [Chrome 확장]  [Wear OS]  [Discord · Mattermost 봇]
      │            │            │              │
      └────────────┴──────┬─────┴──────────────┘
                          ▼
                  Spring Boot API
        (JWT 인증 · 워크스페이스 권한 · SSE 실시간 반영)
                          │
        저장 즉시 201 ─────┤
                          ▼
              Redis Streams  woojuin:item-processing
                          │      (컨슈머 3 · 재시도 · 유실 회수)
                          ▼
        ┌───────── 백그라운드 워커 ─────────┐
        │  URL   → Jsoup ─(실패 시)→ 크롤러 사이드카(Scrapling)
        │  IMAGE → S3 업로드 · 썸네일 · EXIF 좌표 · 비전 모델(OCR·설명)
        │  MEMO  → 본문 정리
        │            └→ ai-mix: 제목·요약 → 카테고리 분류 → 임베딩 → 3D 좌표(UMAP)
        └──────────────────┬───────────────┘
                           ▼
            PostgreSQL (+pgvector)   ·   S3   ·   FCM 푸시
```

- **비동기 처리** — 저장 경로에 AI 호출이 없습니다. 큐 소비는 재시도·유실 회수·재발행 안전망까지 갖춰 두어, 워커가 죽어도 아이템이 `PROCESSING`에 영원히 남지 않습니다
- **없어도 뜬다** — 크롤러·ai-mix·비전·지오코딩·푸시는 모두 키나 사이드카가 없으면 **해당 기능만 조용히 꺼지고** 앱은 정상 기동합니다

---

## 🧰 기술 스택

| 영역                       | 선택                                                                                 | 메모                                         |
| -------------------------- | ------------------------------------------------------------------------------------ | -------------------------------------------- |
| 웹앱                       | React 18 + Vite + TypeScript, Tailwind v4, PWA(vite-plugin-pwa)                      | Share Target·오프라인 셸                     |
| 3D · 지도                  | three.js / MapLibre GL + OpenFreeMap                                                 | 지도 타일 무료·API 키 없음                   |
| 상태 관리                  | TanStack Query(서버) + Jotai(클라이언트)                                             |                                              |
| 익스텐션                   | React + Vite, Manifest V3                                                            |                                              |
| 백엔드                     | Spring Boot 3.3, Java 21, Spring Security                                            | 공통 응답 `{status, message, data}`          |
| DB                         | PostgreSQL 16 + **pgvector**                                                         | 스키마는 Flyway가 소유(`ddl-auto=validate`)  |
| 큐                         | Redis Streams                                                                        | 컨슈머 그룹 · pending 회수                   |
| 스토리지                   | AWS S3 (로컬은 MinIO)                                                                | 원본 + webp 썸네일                           |
| AI (요약·분류·임베딩·좌표) | `ai/ai-mix` FastAPI 사이드카 — OpenRouter `qwen3-8b`, `text-embedding-3-small`, UMAP | Structured Outputs로 스키마 강제             |
| AI (이미지)                | OpenRouter `qwen3-vl-8b-instruct`                                                    | OCR·설명·객체 태그                           |
| AI (검색 해석·STT)         | OpenAI 호환(SSAFY GMS) `gpt-5-mini`, `whisper-1`                                     | 키 없으면 규칙 기반 폴백                     |
| 크롤러                     | `crawler` FastAPI 사이드카 — Scrapling StealthyFetcher                               | Jsoup 실패 시에만 호출                       |
| 지오코딩                   | 카카오 로컬 REST API                                                                 | 주소↔좌표 전용(지도 SDK 아님)                |
| 인증                       | JWT(액세스 1h / 리프레시 14d) + 이메일 가입 + 구글 OAuth + 기기 링크 코드(워치)      | 워치는 키보드가 없어 6자리 코드로 로그인     |
| 알림                       | SSE(실시간 갱신) + FCM Web Push                                                      |                                              |
| 워치                       | Kotlin + Compose for Wear OS (standalone)                                            | 타일·컴플리케이션 포함                       |
| 인프라                     | AWS EC2 · Docker Compose · Nginx                                                     | dev / prod 스택 분리                         |
| CI/CD                      | **Jenkins** Multibranch Pipeline                                                     | `develop`→dev, `main`→prod, 변경 경로만 빌드 |
| 모니터링                   | Prometheus · Grafana · Loki · Promtail                                               | `/actuator/prometheus`                       |

---

## 📁 레포 구조 (모노레포)

```
S15P11C105/
├── frontend/     # 웹앱 (React + Vite + TS, PWA)
├── extension/    # Chrome 익스텐션 (Manifest V3)
├── backend/      # API 서버 (Spring Boot, Java 21)
├── ai/ai-mix/    # AI 사이드카 (FastAPI) — 요약·분류·임베딩·3D 좌표
├── crawler/      # 크롤링 사이드카 (FastAPI + Scrapling)
├── wearos/       # Wear OS 앱 (Kotlin + Compose)
├── monitoring/   # Prometheus · Grafana · Loki 스택
├── docs/         # 컨벤션 및 운영 문서
├── Jenkinsfile             # CI/CD 파이프라인
├── docker-compose.yml      # 로컬 인프라 (PostgreSQL + Redis + MinIO + 크롤러)
└── docker-compose.deploy.yml  # 배포 스택
```

---

## 🚀 로컬에서 실행하기

### 0. 사전 준비

- Node.js 20+ / Java 21 / Docker / (선택) Python 3.11~3.12
- 루트에 `.env` 생성: `cp .env.example .env` — 키가 비어 있어도 앱은 뜹니다(해당 기능만 비활성)

### 1. 인프라

```bash
docker compose up -d
# PostgreSQL(pgvector) + Redis + MinIO + 크롤러(:8001) + ai-mix(:8002)
```

### 2. 백엔드

```bash
cd backend
./gradlew bootRun             # http://localhost:8080  (Swagger: /swagger-ui/index.html)
```

> 사이드카를 띄우지 않고 백엔드만 돌리려면 `AIMIX_ENABLED=false`, `CRAWLER_ENABLED=false`로 끄면 됩니다 — 요약·분류와 크롤 폴백만 비활성화됩니다.
> 사이드카 코드를 직접 고칠 때는 컨테이너 대신 로컬에서 띄우면 됩니다: `cd ai/ai-mix && uvicorn app:app --port 8002` (크롤러는 `cd crawler && uvicorn main:app --port 8001`).

### 3. 웹앱

```bash
cd frontend
npm install && npm run dev    # http://localhost:5173
```

### 4. Chrome 익스텐션

```bash
cd extension
npm install && npm run build:local
```

`chrome://extensions` → 개발자 모드 → "압축해제된 확장 프로그램 로드" → `extension/dist` 선택.
빌드 모드별 연결 대상과 웹스토어 배포 절차는 [Chrome 익스텐션 안내](docs/CHROME_EXTENSION.md) 참고.

### 5. Wear OS (선택)

Android Studio로 **`wearos/` 폴더를 직접** 엽니다(루트 아님). 자세한 내용은 [wearos/README.md](wearos/README.md).

---

## 🧪 검증 명령

```bash
cd frontend && npm run lint && npm run type-check && npm run test
cd extension && npm run build
cd backend  && ./gradlew test
cd ai/ai-mix && python -m pytest                  # 실제 API 호출 없음
```

---

## 🤝 기여 규칙

- 브랜치: `{타입}/{파트}-{설명}` (파트: `FE` / `BE` / `EXT` / `WEAR`) — 예: `feature/BE-item-save-api`
- 기본 브랜치 `main`, 통합 브랜치 `develop`. 둘 다 보호 브랜치라 **MR로만** 반영합니다(리뷰어 1인 이상 승인)
- 커밋: Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`)

자세한 규칙은 [docs/CONVENTIONS.md](docs/CONVENTIONS.md)에 있습니다.

## 📖 문서

### 서비스 문서

| 문서                                       | 내용                                                            |
| ------------------------------------------ | --------------------------------------------------------------- |
| 📋 [기획서](docs/PLANNING.md)              | 문제 정의, 타깃·시나리오, 기능 범위, 화면 구조, 설계 결정, 일정 |
| ✅ [요구사항 명세서](docs/REQUIREMENTS.md) | 기능 요구사항 63개 · 비기능 요구사항 18개                       |
| 🔌 [API 명세서](docs/API.md)               | 엔드포인트 전체 · 공통 응답 규약 · SSE 이벤트 · 에러 코드       |
| 🗄 [ERD](docs/ERD.md)                      | 15개 테이블 관계도 · 컬럼 상세 · 인덱스 전략 · 알려진 부채      |
| 🎬 [시연 시나리오](https://www.figma.com/design/zWIwdtjk1xYT0fwPjdrkvF/Scenario?node-id=258-2468) | 실제 화면으로 따라가는 사용 흐름 (Figma) |

> 위 네 MD 문서는 **현재 코드를 기준으로** 작성했습니다(Flyway 마이그레이션·컨트롤러 기준).
> 노션 원본과 어긋나면 코드에 반영된 최신 결정이 우선입니다.

### 개발 · 운영 문서

| 문서                                                                         | 내용                               |
| ---------------------------------------------------------------------------- | ---------------------------------- |
| [docs/CONVENTIONS.md](docs/CONVENTIONS.md)                                   | 브랜치·커밋·MR 컨벤션              |
| [docs/CHROME_EXTENSION.md](docs/CHROME_EXTENSION.md)                         | 익스텐션 빌드·배포·CORS            |
| [docs/CHAT_INTEGRATIONS_DEPLOYMENT.md](docs/CHAT_INTEGRATIONS_DEPLOYMENT.md) | Discord·Mattermost 봇 설정         |
| [ai/ai-mix/readme.md](ai/ai-mix/readme.md)                                   | AI 사이드카 API 명세               |
| [crawler/README.md](crawler/README.md)                                       | 크롤러 폴백 전략                   |
| [wearos/README.md](wearos/README.md)                                         | 워치 앱 빌드·기기 연결             |
| [AGENTS.md](AGENTS.md)                                                       | AI 코딩 에이전트용 프로젝트 가이드 |

발표 자료와 시연 영상은 팀 노션에 있습니다.
