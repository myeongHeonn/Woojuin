# 우주인 (Woojuin) — 우리 주변의 인포메이션

> **저장은 1초, 정리는 AI가, 찾을 땐 검색 한 번.**
>
> 흩어진 링크·사진·메모를 한 곳에 모아 AI가 자동으로 분류·요약·정리해주는 올인원 AI 스크랩북

SSAFY 15기 광주1반 공통프로젝트 (6인 / 2026.07.13 ~ 2026.08.07)

## 레포 구조 (모노레포)

```
S15P11C105/
├── frontend/    # 웹앱 (React + Vite + TS, PWA)
├── extension/   # 크롬 익스텐션 (React + Vite, Manifest V3)
├── backend/     # API 서버 (Spring Boot, Java 21)
├── wearos/      # Wear OS 앱 (Kotlin) — 스트레치 목표, 순차 개발
├── docs/        # 컨벤션 및 프로젝트 문서
└── docker-compose.yml  # 로컬 개발용 PostgreSQL + Redis
```

## 기술 스택

| 영역 | 선택 |
| --- | --- |
| 프론트(웹앱) | React (Vite) + TypeScript, PWA (vite-plugin-pwa) |
| 프론트(익스텐션) | React + Vite, Manifest V3 |
| 상태 관리 | TanStack Query (서버 상태) + Jotai (클라이언트 상태) |
| 백엔드 | Spring Boot (Java 21) |
| DB | PostgreSQL (AWS RDS) |
| 비동기 큐 | Redis (Redis Streams) |
| AI | OpenAI API (gpt-4o-mini) |
| 지도 | **미정** (어댑터 패턴으로 추상화 예정) |
| 푸시 알림 | PWA + Web Push (FCM) |
| 인증 | JWT + OAuth (카카오/구글) |
| 인프라 | AWS EC2 + Docker + Nginx, S3 |
| CI/CD | GitLab CI |

## 로컬 개발 시작하기

### 0. 사전 준비

- Node.js 20+ / Java 21 / Docker

### 1. 인프라 (PostgreSQL + Redis)

```bash
docker compose up -d
```

### 2. 백엔드

```bash
cd backend
# 최초 1회: gradle wrapper 생성 (로컬에 gradle 설치돼 있다면)
#   gradle wrapper --gradle-version 8.8
./gradlew bootRun
```

- 서버: http://localhost:8080
- 환경변수: 루트의 `.env.example` 참고

### 3. 프론트엔드 (웹앱)

```bash
cd frontend
npm install
npm run dev
```

- 개발 서버: http://localhost:5173

### 4. 크롬 익스텐션

```bash
cd extension
npm install
npm run build
```

- `chrome://extensions` → 개발자 모드 → "압축해제된 확장 프로그램 로드" → `extension/dist` 선택

## 브랜치 / 커밋 / MR 컨벤션

[docs/CONVENTIONS.md](docs/CONVENTIONS.md) 참고. 요약:

- 브랜치: `feature/FE-{이슈번호}-{설명}`, `feature/BE-{이슈번호}-{설명}` / 기본 브랜치는 `master`, 통합 브랜치는 `develop`
- 커밋: Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`)
- 머지: MR 필수, 리뷰 1인 이상 승인 후 머지

## 문서

- 기획서 / 요구사항 명세서 / API 명세서 / 데이터 처리 흐름: 팀 노션 참고
- 공통 API 응답 형식: `{ "status": 200, "message": "success", "data": {} }`
