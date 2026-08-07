# API 명세서 — 우주인

> 이 문서는 **`backend/src/main/java/.../controller/*.java` 코드를 기준으로** 작성했습니다.
> 서버를 띄우면 실행 중인 스펙을 Swagger UI에서 직접 확인할 수 있습니다:
> `http://localhost:8080/swagger-ui/index.html`

| 환경 | Base URL |
| --- | --- |
| 로컬 | `http://localhost:8080/api` |
| 개발 | `https://api.dev.woojuin.store/api` |
| 운영 | `https://api.woojuin.store/api` |

## 목차

- [공통 규약](#공통-규약)
- [인증 · 계정](#인증--계정)
- [워크스페이스](#워크스페이스)
- [멤버 · 초대](#멤버--초대)
- [아이템](#아이템)
- [검색](#검색)
- [뷰 전용 조회 (성좌 · 지도 · 장소)](#뷰-전용-조회-성좌--지도--장소)
- [카테고리](#카테고리)
- [알림](#알림)
- [실시간 변경 신호 (SSE)](#실시간-변경-신호-sse)
- [워치 (음성 · 노래)](#워치-음성--노래)
- [메신저 연동](#메신저-연동)
- [에러 코드](#에러-코드)

---

## 공통 규약

### 응답 형식

성공·실패를 가리지 않고 **모든 응답은 같은 봉투**를 씁니다 (`ApiResponse`).

```json
{
  "status": 200,
  "message": "success",
  "data": { }
}
```

- `status` — HTTP 상태 코드와 같은 값
- `message` — 성공은 `"success"`, 실패는 사용자에게 보여줄 수 있는 한국어 메시지
- `data` — 실패 시 `null`

### 인증

```
Authorization: Bearer <accessToken>
```

- 액세스 토큰 만료 **1시간**, 리프레시 토큰 **14일**
- 토큰이 없거나 만료면 `401`, 워크스페이스 권한이 없으면 `403`
- 인증이 **필요 없는** 엔드포인트: 회원가입 · 로그인 · 이메일 중복확인 · 토큰 갱신 · 워치 기기 링크 시작/폴링 · 초대 미리보기 · 메신저 콜백(별도 서명 검증)

### 페이지네이션

목록·검색 응답은 같은 모양입니다.

```json
{ "content": [ ], "page": 0, "size": 28, "totalElements": 137 }
```

- `page`는 0부터, `size` 기본 **28**, **최대 100**(초과 요청은 100으로 잘립니다)

### 아이템 요약 객체 (`ItemSummaryResponse`)

목록·검색·휴지통이 공통으로 돌려주는 항목입니다.

```json
{
  "itemId": 101,
  "type": "URL",
  "status": "DONE",
  "title": "참깨와 파를 곁들인 소바 국물면",
  "url": "https://...",
  "summary": "참깨와 파 등의 고명이 올라간 일본식 소바 국물면이다.",
  "preview": { "thumbnailUrl": "https://...", "description": "..." },
  "imageUrl": "https://...",
  "categories": [ { "categoryId": 5, "name": "음식·맛집", "color": "#..." } ],
  "favorite": false,
  "createdAt": "2026-08-07T12:00:00Z",
  "deletedAt": null
}
```

- `type` — `URL` / `IMAGE` / `MEMO`
- `status` — `PROCESSING` / `DONE` / `PARTIAL` / `FAILED`
- `imageUrl` — IMAGE 아이템의 presigned URL (목록은 썸네일, 상세는 원본)

---

## 인증 · 계정

### `POST /api/auth/signup` — 이메일 회원가입

```json
{ "email": "a@b.com", "password": "12345678", "nickname": "우주인" }
```

- `password` 8자 이상, `nickname` 50자 이내
- 응답: `UserProfileResponse` (가입과 동시에 개인 스페이스가 생성되고 `personalSpaceId`로 내려옵니다)

### `GET /api/auth/check-email?email=` — 이메일 사용 가능 여부

```json
{ "available": true }
```

### `POST /api/auth/login`

```json
{ "email": "a@b.com", "password": "12345678" }
```

응답: `{ "accessToken": "...", "refreshToken": "..." }`

### `POST /api/auth/token/refresh`

```json
{ "refreshToken": "..." }
```

### `POST /api/auth/logout` 🔒

현재 세션만 무효화합니다.

### 구글 OAuth

브라우저를 **백엔드 루트 경로**로 이동시킵니다(`/api` 아래가 아닙니다).

```
GET {백엔드 origin}/oauth2/authorization/google
```

성공하면 `woojuin.oauth.redirect-base-url`(기본 `http://localhost:5173/oauth/callback`)로 토큰을
붙여 돌려보냅니다. 신규 가입이면 프론트가 온보딩(`/oauth/onboarding`)으로 보냅니다.

> 카카오 로그인은 **미구현**입니다(설정 자리만 있습니다). 카카오는 지오코딩 REST API로만 쓰입니다.

### `GET /api/users/me` 🔒 — 내 프로필

```json
{
  "id": 1, "email": "a@b.com", "nickname": "우주인",
  "profileImageUrl": null, "provider": "LOCAL", "emailVerified": false,
  "personalSpaceId": 1, "avatarColor": "BLUE",
  "personalTutorialCompleted": false,
  "sharedWorkspaceTutorialCompleted": false
}
```

### `PATCH /api/users/me` 🔒 — 프로필 수정

```json
{ "profileImageUrl": "https://...", "avatarColor": "PURPLE" }
```

`avatarColor`: `RED` `ORANGE` `YELLOW` `GREEN` `BLUE` `NAVY` `PURPLE` `PINK` `CRIMSON` `BLACK` `WHITE`

### `GET /api/users/me/stats` 🔒

```json
{ "totalSaved": 137, "workspaceCount": 3, "savedThisWeek": 12 }
```

### `GET /api/users/me/ai-usage` 🔒 — 월 AI 사용량

```json
{
  "period": "2026-08", "used": 42, "limit": 500, "remaining": 458,
  "unlimited": false, "limitEnabled": true, "resetAt": "2026-09-01T00:00:00Z"
}
```

한도를 넘기면 저장 시 `429`가 납니다. 워크스페이스의 대표 OWNER 사용량으로 계산합니다.

### `PATCH /api/users/me/tutorials/{tutorialType}/complete` 🔒

`tutorialType`: `PERSONAL` / `SHARED_WORKSPACE`

### `DELETE /api/users/me` 🔒 — 회원 탈퇴

소프트 삭제 + 로그인 식별자(이메일·provider_id) 파기. 같은 소셜 계정으로 재가입할 수 있습니다.

### 세션 (연결된 기기)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/auth/sessions` 🔒 | 목록 (`sessionId`, `deviceName`, `createdAt`, `lastUsedAt`, `current`) |
| `DELETE` | `/api/auth/sessions/{sessionId}` 🔒 | 해당 기기 로그아웃 |
| `DELETE` | `/api/auth/sessions` 🔒 | 전체 로그아웃 |

### 기기 링크 (Wear OS 로그인)

워치에 키보드가 없어 **6자리 코드**로 로그인합니다.

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/device-link` | — | 워치가 호출 → `{ "code": "123456", "expiresInSeconds": 300 }` |
| `POST` | `/api/auth/device-link/approve` | 🔒 | 웹(마이페이지)에서 코드 입력 → 승인 |
| `POST` | `/api/auth/device-link/poll` | — | 워치가 폴링 → `{ "status": "PENDING" }` 또는 `{ "status": "APPROVED", "accessToken": "...", "refreshToken": "..." }` |

> **토큰 폐기 규칙** — 통신 실패·타임아웃·5xx에는 워치가 토큰을 버리지 않습니다.
> 서버가 명시적으로 거부한 `400`·`401`·`403`에만 지웁니다.

---

## 워크스페이스

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `POST` | `/api/workspaces` 🔒 | 생성 → `201`. `{ "name": "...", "type": "TEAM" }` |
| `GET` | `/api/workspaces` 🔒 | 내가 속한 목록 |
| `GET` | `/api/workspaces/{workspaceId}` 🔒 | 단건 |
| `GET` | `/api/workspaces/{workspaceId}/preview` 🔒 | 이름만 (초대 화면용) |
| `PATCH` | `/api/workspaces/{workspaceId}` 🔒 | 이름 변경 (OWNER) |
| `DELETE` | `/api/workspaces/{workspaceId}` 🔒 | 삭제 (OWNER) |

`WorkspaceResponse`

```json
{ "id": 1, "name": "우리 팀", "type": "TEAM", "role": "OWNER" }
```

- `type` — `PERSONAL`(가입 시 자동 생성, 삭제 불가) / `TEAM`
- `role` — 요청자의 역할 (`OWNER` / `MEMBER`)

**생성 시 기본 카테고리 11종이 함께 시드됩니다.**

---

## 멤버 · 초대

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/workspaces/{workspaceId}/invitations` 🔒 | OWNER | 초대 코드 발급 → `201` |
| `GET` | `/api/invitations/{code}` | — | **로그인 전 미리보기** (워크스페이스 이름·만료 시각) |
| `POST` | `/api/invitations/{code}/accept` 🔒 | | 참여 → `WorkspaceResponse` |
| `GET` | `/api/workspaces/{workspaceId}/members` 🔒 | | 멤버 목록 |
| `PATCH` | `/api/workspaces/{workspaceId}/members/{userId}` 🔒 | OWNER | 역할 변경 `{ "role": "OWNER" }` |
| `DELETE` | `/api/workspaces/{workspaceId}/members/{userId}` 🔒 | OWNER 또는 본인 | 추방 / 자진 탈퇴 |
| `GET` | `/api/workspaces/{workspaceId}/member-activities` 🔒 | | 활동 피드 (`JOINED`/`LEFT`/`KICKED`) |
| `GET` | `/api/workspaces/{workspaceId}/item-activity` 🔒 | | `{ "hasNewActivity": true }` — 새 아이템 배지 |
| `PUT` | `/api/workspaces/{workspaceId}/item-activity/last-seen` 🔒 | | 배지 읽음 처리 |

- **추방된 사용자는 초대 링크로도 재입장할 수 없습니다** (`403 WorkspaceBanned`). 자진 탈퇴는 재입장 가능
- **마지막 OWNER는 역할을 내리거나 나갈 수 없습니다** (`400 WorkspaceLastOwner`)
- 탈퇴한 사용자는 목록·피드에서 닉네임이 `"탈퇴한 사용자"`, `withdrawn: true`로 내려옵니다

---

## 아이템

### `POST /api/workspaces/{workspaceId}/items` 🔒 — URL · 메모 저장

`Content-Type: application/json`

```json
{ "type": "URL",  "url": "https://example.com/article" }
{ "type": "MEMO", "content": "다음 회의까지 검색 UI 마무리" }
```

- `type=URL`이면 `url` 필수 · `content` 금지, `type=MEMO`면 그 반대 (위반 시 `400`)
- 응답 `201` — **AI 처리를 기다리지 않고 즉시** 돌려줍니다

```json
{ "itemId": 101, "status": "PROCESSING", "createdAt": "2026-08-07T12:00:00Z" }
```

### `POST /api/workspaces/{workspaceId}/items` 🔒 — 사진 저장

`Content-Type: multipart/form-data`, 파트 이름 `file` (최대 **10MB**)

응답은 위와 동일한 `201 PROCESSING`. 뒤에서 S3 업로드 → 썸네일 → EXIF 좌표 → 비전 분석이 돕니다.

### `GET /api/workspaces/{workspaceId}/items` 🔒 — 목록

| 파라미터 | 기본값 | 설명 |
| --- | --- | --- |
| `type` | — | `URL` / `IMAGE` / `MEMO` |
| `status` | — | `PROCESSING` / `DONE` / `PARTIAL` / `FAILED` |
| `favorite` | — | `true`면 즐겨찾기만 |
| `categoryIds` | — | 반복 파라미터 (`?categoryIds=1&categoryIds=2`) |
| `sort` | `latest` | `latest`(생성 역순) / `title`(제목 오름차순) |
| `page` / `size` | `0` / `28` | |

### `GET /api/workspaces/{workspaceId}/trash` 🔒 — 휴지통

최근 삭제 순. 응답 모양은 목록과 같습니다.

### 아이템 단건 (`/api/items/{itemId}`)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/items/{itemId}` 🔒 | 상세 (`content` 포함, IMAGE는 **원본** presigned URL) |
| `GET` | `/api/items/{itemId}/status` 🔒 | `{ "itemId": 101, "status": "DONE" }` — 알림을 거부한 사용자를 위한 폴링 폴백 |
| `PATCH` | `/api/items/{itemId}` 🔒 | 제목·본문·카테고리 수정 |
| `POST` | `/api/items/{itemId}/favorite` 🔒 | 즐겨찾기 추가 |
| `DELETE` | `/api/items/{itemId}/favorite` 🔒 | 즐겨찾기 해제 |
| `DELETE` | `/api/items/{itemId}` 🔒 | **휴지통으로 이동** (soft delete) |
| `POST` | `/api/items/{itemId}/restore` 🔒 | 휴지통에서 복원 |
| `DELETE` | `/api/items/{itemId}/permanent` 🔒 | 영구 삭제 (휴지통에 있는 아이템만) |

`PATCH` 본문

```json
{ "title": "새 제목", "content": "수정한 메모", "categoryIds": [5, 6] }
```

- `title` 500자 이내, `categoryIds`를 보낼 경우 **최소 1개** (빈 배열 불가)
- 세 필드 모두 선택 — 보낸 것만 반영됩니다

> **삭제는 항상 휴지통이 먼저입니다.** `DELETE /api/items/{id}`는 영구 삭제가 아닙니다.

---

## 검색

### `GET /api/workspaces/{workspaceId}/search?q=` 🔒 — 키워드 검색

제목 · AI 요약 · 본문(메모/추출 본문/OCR 텍스트) · 미리보기 설명 · 카테고리 이름을 훑습니다.

```json
{
  "content": [ ], "page": 0, "size": 28, "totalElements": 3,
  "partialMatch": false,
  "semanticMatch": false,
  "semanticSupplementCount": 0
}
```

| 필드 | 의미 |
| --- | --- |
| `partialMatch` | 검색어를 모두 만족하는 결과가 없어 일부 단어로 완화했음 |
| `semanticMatch` | 키워드 0건이라 **의미(임베딩) 검색**으로 넘어간 결과 |
| `semanticSupplementCount` | 키워드 결과에 의미 검색으로 덧붙인 건수 |

### `GET /api/workspaces/{workspaceId}/ai/search?q=` 🔒 — AI 검색

`q`가 키워드가 아니라 **자연어 문장**입니다 — `"그 파스타집 어디였지?"`.
파라미터 이름을 키워드 검색과 맞춰 두어 프론트는 토글만으로 경로를 바꿉니다.

```json
{
  "content": [ ], "page": 0, "size": 28, "totalElements": 2,
  "interpretedQuery": "파스타 식당",
  "aiPlanned": true,
  "partialMatch": false, "semanticMatch": true, "semanticSupplementCount": 0
}
```

- `interpretedQuery` — 문장에서 뽑아낸 실제 검색어(화면에 "이렇게 이해했어요"로 표시)
- `aiPlanned` — `false`면 AI 키가 없어 **규칙 기반**으로 해석했다는 뜻(오타 교정이 빠집니다)
- 의미 검색은 코사인 거리 상한(기본 `0.7`)을 넘는 아이템을 "관련 없음"으로 버립니다

---

## 뷰 전용 조회 (성좌 · 지도 · 장소)

### `GET /api/workspaces/{workspaceId}/universe` 🔒 — 성좌(3D) 뷰

카테고리별로 묶인 별 목록. `position`은 UMAP으로 축소한 3차원 좌표입니다.

```json
{
  "constellations": [
    {
      "categoryId": 5, "categoryName": "음식·맛집", "color": "#F97316",
      "items": [ { "id": 101, "position": [12.4, -3.1, 8.0], "title": "...", "type": "URL" } ]
    }
  ],
  "unclassified": [ { "id": 140, "position": [0, 0, 0], "title": "...", "type": "MEMO" } ]
}
```

`unclassified`는 아직 좌표가 계산되지 않았거나 카테고리가 없는 아이템입니다.

### `GET /api/workspaces/{workspaceId}/items/geo` 🔒 — 지도 뷰

좌표가 있는 활성 아이템을 **페이지네이션 없이 한 번에** 돌려줍니다.

```json
[ { "itemId": 101, "type": "IMAGE", "title": "...", "categoryIds": [5],
    "favorite": false, "lat": 35.15, "lng": 126.89, "address": "광주 광산구 ..." } ]
```

좌표는 세 경로로 들어옵니다 — ① 사진 EXIF의 GPS, ② 지도 공유 링크 URL에서 직접 추출,
③ 본문 주소를 카카오 로컬 API로 지오코딩.

### `GET /api/places/nearby?lat=&lng=&expand=` 🔒 — 주변 장소 후보

워치의 "지금 있는 곳 저장"이 쓰는 거리순 후보 목록. `expand=true`면 반경을 넓힙니다.

```json
{
  "candidates": [ { "name": "○○카페", "category": "카페", "distanceMeters": 42,
                    "lat": 35.15, "lng": 126.89, "address": "...", "placeUrl": "..." } ],
  "expanded": false
}
```

---

## 카테고리

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/workspaces/{workspaceId}/categories?hasItems=` 🔒 | 목록. `hasItems=true`면 아이템이 있는 것만 |
| `POST` | `/api/workspaces/{workspaceId}/categories` 🔒 | 생성 → `201`. `{ "name": "사이드 프로젝트" }` (50자 이내) |
| `PATCH` | `/api/workspaces/{workspaceId}/categories/{categoryId}` 🔒 | 이름 변경 |
| `DELETE` | `/api/workspaces/{workspaceId}/categories/{categoryId}` 🔒 | 삭제 |

- `기타` 카테고리는 **삭제할 수 없습니다** — AI 분류 실패 시 폴백 대상이라 항상 존재해야 합니다
- 새 카테고리를 만들면 설명(AI 분류 기준)이 비동기로 생성됩니다

---

## 알림

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/notifications` 🔒 | 인앱 알림함 |
| `POST` | `/api/notifications/test` 🔒 | 푸시 발송 테스트 |
| `POST` | `/api/notifications/tokens` 🔒 | FCM 토큰 등록 → `201`. `{ "token": "...", "deviceInfo": "Chrome/Windows" }` |
| `DELETE` | `/api/notifications/tokens` 🔒 | 토큰 삭제. `{ "token": "..." }` |

**발송 정책** — 아이템 처리가 `DONE` 또는 `PARTIAL`이면 푸시를 보내고, `FAILED`는 보내지 않습니다.
FCM 서비스 계정 키가 없으면 로그만 남기고 실제 발송은 생략됩니다(앱은 정상 동작).

---

## 실시간 변경 신호 (SSE)

### `GET /api/workspaces/{workspaceId}/events` 🔒

`Content-Type: text/event-stream`. 워크스페이스 화면이 열려 있는 동안 한 연결을 유지합니다.

| 이벤트 이름 | 발생 시점 |
| --- | --- |
| `connected` | 구독 성공 (data = workspaceId) |
| `item` | 아이템 생성 · **AI 처리 완료** · 수정 · 삭제 · 즐겨찾기 · 휴지통 |
| `category` | 카테고리 추가 · 이름변경 · 삭제 |
| `workspace` | 워크스페이스 이름변경 · 삭제 |
| `member` | 초대 수락 · 멤버 제거 · 탈퇴 |

- 이벤트 타입을 동작 단위로 쪼개지 않고 **리소스 단위로 묶었습니다** — 클라이언트가 하는 일은 결국 관련 캐시 무효화라, 무효화 대상이 같은 것끼리 한 타입이면 유지보수가 쉽습니다
- 주석 형태의 하트비트(`:ping`)를 주기적으로 보냅니다 — nginx 유휴 타임아웃(60초)에 끊기지 않기 위한 것입니다
- 멤버가 아니면 `403`, 미인증이면 `401` (스트림을 열지 않고 일반 JSON 에러로 응답)

---

## 워치 (음성 · 노래)

### `POST /api/speech/transcriptions` 🔒

`multipart/form-data`, 파트 `file` (오디오)

```json
{ "text": "내일 아침에 그 카페 다시 가보기" }
```

**아이템을 만들지 않습니다** — 글자만 돌려주고, 저장할지 검색어로 쓸지는 워치가 정합니다.

### `POST /api/music/recognitions` 🔒

`multipart/form-data`, 파트 `file` (오디오). 들리는 노래를 인식합니다.
`AUDD_API_TOKEN`이 없으면 이 기능만 꺼지고 `503`이 납니다.

---

## 메신저 연동

### 사용자용 (웹 마이페이지)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `POST` | `/api/integrations/chat/link-code` 🔒 | 메신저에서 입력할 연결 코드 발급 → `{ "code": "...", "expiresAt": "..." }` |
| `GET` | `/api/integrations/connections` 🔒 | 연결된 메신저 계정 목록 |
| `DELETE` | `/api/integrations/connections/{connectionId}` 🔒 | 연결 해제 |
| `GET` | `/api/integrations/channel-mappings` 🔒 | 채널 ↔ 워크스페이스 매핑 목록 |
| `POST` | `/api/integrations/channel-mappings` 🔒 | 매핑 생성 → `201`. `{ "platform": "DISCORD", "channelId": "...", "workspaceId": 1 }` |

`platform`: `DISCORD` / `MATTERMOST`. 같은 채널이 이미 매핑돼 있으면 `409`.

### 플랫폼 콜백 (사용자가 직접 부르지 않음)

| 메서드 | 경로 | 인증 방식 |
| --- | --- | --- |
| `POST` | `/api/integrations/discord/interactions` | Ed25519 서명 검증 (`DISCORD_PUBLIC_KEY`) |
| `POST` | `/api/integrations/mattermost/commands` | 슬래시 토큰 검증 (`MATTERMOST_SLASH_TOKEN`). `application/x-www-form-urlencoded` |

토큰이 설정돼 있지 않으면 해당 엔드포인트는 `503`으로 거부합니다.

### 봇 → 우주인 저장

`POST /api/integrations/bot/items`

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| `X-Bot-Secret` | ✓ | `CHAT_BOT_SECRET` |
| `X-Bot-Platform` | ✓ | `DISCORD` / `MATTERMOST` |
| `X-Request-Id` | | 중복 처리 방지 키 |

```json
{ "channelId": "...", "type": "URL", "url": "https://..." }
```

저장 대상 워크스페이스는 **① 채널 매핑 → ② 연결 계정의 기본 워크스페이스** 순으로 결정됩니다.
응답은 `201` + `ItemCreateResponse`, 시크릿이 틀리면 `401`, 매핑을 못 찾으면 `404`.

> 설정 절차는 [CHAT_INTEGRATIONS_DEPLOYMENT.md](CHAT_INTEGRATIONS_DEPLOYMENT.md)에 있습니다.

---

## 에러 코드

에러도 같은 봉투로 내려옵니다.

```json
{ "status": 403, "message": "워크스페이스 멤버만 접근할 수 있습니다", "data": null }
```

| 상태 | 발생 상황 |
| --- | --- |
| `400` | 검증 실패 · 요청 형식 오류 · 만료된 초대 코드 · 참여할 수 없는 초대 · 마지막 OWNER 강등/탈퇴 |
| `401` | 토큰 없음/만료/무효 · 탈퇴한 계정 · 봇 시크릿 불일치 |
| `403` | 워크스페이스 접근 거부 · 멤버 아님 · OWNER 권한 필요 · **추방된 사용자** |
| `404` | 사용자 · 아이템 · 카테고리 · 워크스페이스 · 멤버 · 초대 코드 · 알림 토큰 · 채널 매핑 없음 |
| `409` | 이미 매핑된 채널 |
| `413` | 업로드 파일이 10MB 초과 |
| `429` | 월 AI 사용량 한도 초과 |
| `503` | AI 사용량 확인 불가(Redis) · 노래 인식 불가 · 받아쓰기 불가 · 메신저 연동 미설정 |

---

## 관련 문서

- [ERD](ERD.md) — 응답 필드가 어디서 오는지
- [요구사항 명세서](REQUIREMENTS.md) — 각 엔드포인트가 어떤 요구사항을 만족하는지
- [Chrome 익스텐션 연동](CHROME_EXTENSION.md) — CORS 허용 출처 설정
