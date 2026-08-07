# 요구사항 명세서 — 우주인

> **이 문서는 "코드에 실제로 들어간 것"을 기준으로 정리한 역방향 명세서입니다.**
> 원본 FR/NFR 번호는 팀 노션의 요구사항 DB에 있고, 그중 코드 주석에서 확인된 번호만
> `노션 FR` 열에 표기했습니다. 나머지는 노션 번호를 추측하지 않고 이 문서의 자체 ID만 씁니다.
>
> 상태 표기: ✅ 구현 · 🟡 부분 구현 · ⬜ 미구현(범위 밖 또는 다음 사이클)

## 목차

- [요구사항 요약](#요구사항-요약)
- [기능 요구사항 (FR)](#기능-요구사항-fr)
  - [계정 · 인증](#계정--인증)
  - [저장 (수집)](#저장-수집)
  - [AI 가공](#ai-가공)
  - [조회 · 뷰](#조회--뷰)
  - [검색](#검색)
  - [정리 · 관리](#정리--관리)
  - [협업 (워크스페이스)](#협업-워크스페이스)
  - [알림](#알림)
  - [외부 연동](#외부-연동)
  - [Wear OS](#wear-os)
- [비기능 요구사항 (NFR)](#비기능-요구사항-nfr)
- [범위에서 제외한 것](#범위에서-제외한-것)

---

## 요구사항 요약

| 영역 | 요구사항 수 | ✅ | 🟡 | ⬜ |
| --- | --- | --- | --- | --- |
| 계정 · 인증 | 9 | 7 | 0 | 2 |
| 저장 (수집) | 8 | 8 | 0 | 0 |
| AI 가공 | 7 | 7 | 0 | 0 |
| 조회 · 뷰 | 7 | 6 | 0 | 1 |
| 검색 | 4 | 4 | 0 | 0 |
| 정리 · 관리 | 7 | 7 | 0 | 0 |
| 협업 | 9 | 9 | 0 | 0 |
| 알림 | 4 | 4 | 0 | 0 |
| 외부 연동 | 5 | 5 | 0 | 0 |
| Wear OS | 5 | 5 | 0 | 0 |
| **합계** | **65** | **62** | **0** | **3** |

---

## 기능 요구사항 (FR)

### 계정 · 인증

| ID | 노션 FR | 요구사항 | 상태 | 구현 위치 |
| --- | --- | --- | --- | --- |
| AUTH-01 | FR-001 | 이메일·비밀번호로 회원가입한다 (비밀번호 8자 이상, 닉네임 50자 이내) | ✅ | `POST /api/auth/signup` |
| AUTH-02 | | 가입 전 이메일 중복 여부를 확인한다 | ✅ | `GET /api/auth/check-email` |
| AUTH-03 | FR-001 | 이메일·비밀번호로 로그인하고 액세스·리프레시 토큰을 받는다 | ✅ | `POST /api/auth/login` |
| AUTH-04 | | 구글 계정으로 로그인·가입한다. 첫 로그인이면 온보딩(닉네임·약관 동의)을 거친다 | ✅ | `/oauth2/authorization/google` |
| AUTH-05 | | 액세스 토큰이 만료되면 리프레시 토큰으로 갱신한다 | ✅ | `POST /api/auth/token/refresh` |
| AUTH-06 | | 로그인한 기기 목록을 보고 개별·전체 로그아웃한다 | ✅ | `/api/auth/sessions` |
| AUTH-07 | | 프로필(이미지·아바타 색)을 수정한다 | ✅ | `PATCH /api/users/me` |
| AUTH-08 | | 회원 탈퇴 시 로그인 식별자를 파기해 같은 계정으로 재가입할 수 있다 | ✅ | `DELETE /api/users/me`, V12 |
| AUTH-09 | | 가입 시 이메일 인증코드로 소유를 확인한다 | ⬜ | `email_verified` 컬럼만 존재(항상 `false`), 인증 발송 흐름 없음 |
| AUTH-10 | | 카카오 계정으로 로그인한다 | ⬜ | 설정 자리만 있음. 카카오는 지오코딩 REST API로만 사용 |

### 저장 (수집)

| ID | 노션 FR | 요구사항 | 상태 | 구현 위치 |
| --- | --- | --- | --- | --- |
| SAVE-01 | | 링크(URL)를 저장한다 | ✅ | `POST /items` (`type=URL`) |
| SAVE-02 | | 사진을 저장한다 (10MB 이하) | ✅ | `POST /items` (multipart) |
| SAVE-03 | | 텍스트 메모를 저장한다 | ✅ | `POST /items` (`type=MEMO`) |
| SAVE-04 | FR-025 | 저장물의 처리 상태를 `PROCESSING`/`DONE`/`PARTIAL`/`FAILED`로 관리한다 | ✅ | `ItemStatus`, `items.status` |
| SAVE-05 | | 크롬 확장에서 보고 있는 페이지를 툴바 버튼·우클릭 메뉴로 저장한다 | ✅ | `extension/` |
| SAVE-06 | FR-013 | 모바일 OS 공유 시트에서 우주인을 골라 저장한다 | ✅ | PWA Share Target, `ShareTargetPage` |
| SAVE-07 | | 저장 대상 워크스페이스를 저장 시점에 고른다 | ✅ | 확장의 `SpacePicker`, 웹앱은 현재 워크스페이스 |
| SAVE-08 | | 월 AI 처리 건수 상한을 두어 비용을 보호한다 (기본 500건/월) | ✅ | `GET /api/users/me/ai-usage`, 초과 시 `429` |

### AI 가공

| ID | 노션 FR | 요구사항 | 상태 | 구현 위치 |
| --- | --- | --- | --- | --- |
| AI-01 | | 저장물의 제목을 자동 생성한다 | ✅ | ai-mix `/v1/title-summary/*` |
| AI-02 | | 저장물의 한 줄 요약을 자동 생성한다 | ✅ | 같음 |
| AI-03 | | 저장물을 **워크스페이스에 존재하는** 카테고리로 자동 분류한다 (최대 2개) | ✅ | ai-mix `/v1/categories/classify` |
| AI-04 | | 분류 임계값을 넘는 후보가 없으면 가장 가까운 하나로 폴백하고, 그래도 없으면 `기타`로 넣는다 | ✅ | `CATEGORY_SCORE_THRESHOLD`, `CategoryDefaults.ETC` |
| AI-05 | FR-020 | URL의 실제 본문을 확보한다. 정적 HTML로 안 되면 스텔스 브라우저로 한 번 더 시도한다 | ✅ | `FallbackHtmlFetcher` → `crawler/` |
| AI-06 | | 사진에서 설명·객체 태그·글자(OCR)를 추출해 요약 근거로 쓴다 | ✅ | `QwenVisionImageTextExtractor` |
| AI-07 | | 아이템 임베딩을 생성해 저장하고, 워크스페이스 전체를 3차원 좌표로 축소한다 | ✅ | `item_embeddings`, ai-mix `/v1/coordinates/reduce` |

**트랙 A / 트랙 B** — 모든 콘텐츠 처리는 두 독립 트랙으로 나뉩니다.

- **트랙 A (미리보기)** — oEmbed·OG 태그로 카드 만들기. 거의 항상 성공
- **트랙 B (콘텐츠)** — AI가 요약·분류할 실제 본문 확보. 실패 가능

한쪽 실패가 다른 쪽에 영향을 주지 않아야 하고, A만 성공하면 `PARTIAL`로 남습니다.

### 조회 · 뷰

| ID | 노션 FR | 요구사항 | 상태 | 구현 위치 |
| --- | --- | --- | --- | --- |
| VIEW-01 | | 저장물을 카드 목록으로 본다 (타입·상태·즐겨찾기·카테고리 필터, 최신/제목 정렬) | ✅ | `GET /items`, `LibraryPage` |
| VIEW-02 | | 카테고리 칩으로 목록을 걸러 본다 | ✅ | `CategoryChipBar` |
| VIEW-03 | | 저장물 상세를 본다 | ✅ | `GET /api/items/{id}` |
| VIEW-04 | | 의미가 비슷한 저장물이 가까이 놓인 3D 성좌 뷰로 본다 | ✅ | `GET /universe`, `UniverseCanvas` (three.js) |
| VIEW-05 | FR-032 | 좌표가 있는 저장물을 지도 위에서 본다 | ✅ | `GET /items/geo`, MapLibre + OpenFreeMap |
| VIEW-06 | FR-023 | 사진 EXIF·지도 공유 링크·본문 주소에서 좌표를 확보한다 | ✅ | `ImageItemProcessor`, `UrlItemProcessor`, 카카오 로컬 API |
| VIEW-07 | | 보드형 "한눈에 보기" 뷰에서 자유 배치·코멘트 핀을 쓴다 | ⬜ | `CanvasPage`가 자리표시(`StagePlaceholder`) 상태 |

### 검색

| ID | 노션 FR | 요구사항 | 상태 | 구현 위치 |
| --- | --- | --- | --- | --- |
| SEARCH-01 | FR-030 | 제목·요약·본문·미리보기 설명·카테고리 이름을 한 번에 검색한다 | ✅ | `GET /search` |
| SEARCH-02 | FR-030 | 모든 검색어를 만족하는 결과가 없으면 일부 단어로 완화해 보여주고 그 사실을 표시한다 | ✅ | `partialMatch` |
| SEARCH-03 | | 키워드로 0건이면 임베딩 기반 의미 검색으로 넘어간다 (거리 상한 밖은 버린다) | ✅ | `ItemSemanticSearchService`, `semanticMatch` |
| SEARCH-04 | | 자연어 문장으로 검색한다 ("그 파스타집 어디였지?"). 해석 결과를 사용자에게 보여준다 | ✅ | `GET /ai/search`, `interpretedQuery` |

> AI 키가 없어도 검색은 동작합니다 — 규칙 기반 해석으로 폴백하고 `aiPlanned: false`로 알립니다.

### 정리 · 관리

| ID | 노션 FR | 요구사항 | 상태 | 구현 위치 |
| --- | --- | --- | --- | --- |
| MANAGE-01 | | 저장물의 제목·본문·카테고리를 직접 수정한다 | ✅ | `PATCH /api/items/{id}` |
| MANAGE-02 | | 저장물을 즐겨찾기한다 | ✅ | `POST/DELETE /api/items/{id}/favorite` |
| MANAGE-03 | | 삭제는 항상 휴지통 이동이 먼저다 (soft delete) | ✅ | `items.deleted_at` |
| MANAGE-04 | FR-036 | 휴지통 목록을 최근 삭제 순으로 본다 | ✅ | `GET /trash` |
| MANAGE-05 | | 휴지통에서 복원한다 | ✅ | `POST /api/items/{id}/restore` |
| MANAGE-06 | | 영구 삭제는 휴지통에 있는 항목만 가능하다 | ✅ | `DELETE /api/items/{id}/permanent` |
| MANAGE-07 | | 카테고리를 추가·이름변경·삭제한다. 단 `기타`는 삭제할 수 없다 | ✅ | `/categories` |

### 협업 (워크스페이스)

| ID | 노션 FR | 요구사항 | 상태 | 구현 위치 |
| --- | --- | --- | --- | --- |
| WS-01 | | 가입 시 개인 스페이스가 자동 생성된다 (삭제 불가) | ✅ | `users.personal_workspace_id`, `type=PERSONAL` |
| WS-02 | | 공유 워크스페이스를 만들고 이름을 바꾸고 삭제한다 | ✅ | `/api/workspaces` |
| WS-03 | | 워크스페이스 생성 시 기본 카테고리 11종이 시드된다 | ✅ | `CategoryDefaults` |
| WS-04 | | 초대 링크(코드)로 멤버를 초대한다. 링크는 만료된다 | ✅ | `workspace_invitations` |
| WS-05 | | 로그인 전에도 초대 링크로 워크스페이스 이름을 미리 본다 | ✅ | `GET /api/invitations/{code}` |
| WS-06 | | 멤버 권한은 OWNER/MEMBER 두 단계다. 모든 아이템 접근은 멤버십으로 인가한다 | ✅ | `workspace_members` |
| WS-07 | | 추방된 사용자는 초대 링크로도 재입장할 수 없다. 자진 탈퇴는 재입장할 수 있다 | ✅ | `workspace_bans` |
| WS-08 | | 마지막 OWNER는 강등·탈퇴할 수 없다 | ✅ | `WorkspaceLastOwnerException` |
| WS-09 | | 멤버 활동(가입·탈퇴·추방)과 새 아이템 여부를 피드·배지로 확인한다 | ✅ | `member-activities`, `item-activity` |

### 알림

| ID | 노션 FR | 요구사항 | 상태 | 구현 위치 |
| --- | --- | --- | --- | --- |
| NOTI-01 | FR-050 | AI 처리가 끝나면 웹 푸시로 알린다 (`DONE`·`PARTIAL`만, `FAILED`는 발송 생략) | ✅ | FCM, `frontend/src/sw.ts` |
| NOTI-02 | | 알림을 거부한 사용자는 상태 조회 API로 완료를 확인할 수 있다 | ✅ | `GET /api/items/{id}/status` |
| NOTI-03 | | 인앱 알림함에서 지난 알림을 본다 | ✅ | `GET /api/notifications` |
| NOTI-04 | | 다른 멤버의 저장·수정이 화면에 실시간 반영된다 | ✅ | SSE `GET /events` |

### 외부 연동

| ID | 요구사항 | 상태 | 구현 위치 |
| --- | --- | --- | --- |
| INT-01 | Discord 채널에서 슬래시 명령으로 저장한다 | ✅ | `/api/integrations/discord/interactions` |
| INT-02 | Mattermost 채널에서 슬래시 명령으로 저장한다 | ✅ | `/api/integrations/mattermost/commands` |
| INT-03 | 메신저 계정과 우주인 계정을 연결 코드로 잇는다 | ✅ | `/api/integrations/chat/link-code` |
| INT-04 | 채널을 워크스페이스에 매핑해, 그 채널의 저장이 해당 워크스페이스로 가게 한다 | ✅ | `chat_channel_mappings` |
| INT-05 | 같은 슬래시 명령이 두 번 처리되지 않는다 | ✅ | `X-Request-Id` + Redis 중복 방지 키 |

### Wear OS

| ID | 노션 FR | 요구사항 | 상태 | 구현 위치 |
| --- | --- | --- | --- | --- |
| WEAR-01 | | 키보드 없이 6자리 코드로 워치에 로그인한다 | ✅ | `/api/auth/device-link` |
| WEAR-02 | FR-053 | 워치에서 지금 있는 장소를 저장한다 (주변 장소 후보에서 고르기) | ✅ | `GET /api/places/nearby`, `RemotePlaceRepository` |
| WEAR-03 | FR-054 | 워치에서 음성으로 메모를 저장한다 (오디오 → 받아쓰기) | ✅ | `POST /api/speech/transcriptions` |
| WEAR-04 | FR-055 | 워치에서 듣고 있는 노래를 인식해 저장한다 | ✅ | `POST /api/music/recognitions` (AudD) |
| WEAR-05 | | 워치에서 저장물을 검색한다 | ✅ | `RemoteSearchRepository` |

---

## 비기능 요구사항 (NFR)

| ID | 노션 NFR | 요구사항 | 어떻게 만족하는가 | 상태 |
| --- | --- | --- | --- | --- |
| NFR-P1 | NFR-001 | **"저장은 1초"** — 저장 요청은 AI 처리를 기다리지 않고 즉시 응답한다 | 저장 API가 `201 PROCESSING`을 바로 반환하고, 가공은 Redis Streams로 넘긴다. **저장 경로에 동기 AI 호출을 넣지 않는다** | ✅ |
| NFR-P2 | | 목록 조회는 대상이 커져도 앞 페이지만 읽는다 | `idx_items_ws_active` 부분 인덱스 — 20만 건/대상 1만 건에서 6.4ms → 0.19ms | ✅ |
| NFR-P3 | | 지도 조회는 좌표 보유 아이템만 훑는다 | `idx_items_ws_geo` 부분 인덱스 | ✅ |
| NFR-R1 | | 큐 메시지가 유실되거나 영구 `PROCESSING`으로 남지 않는다 | 컨슈머 그룹 + pending 회수기(3분 유휴) + 재발행 안전망(15분) + 최대 배달 3회 후 `FAILED` 확정 | ✅ |
| NFR-R2 | | 같은 아이템을 두 번 가공하지 않는다 | 회수 유휴 시간(180초)을 최악 처리 시간(크롤러 폴백 40s×2 + ai-mix 45s)보다 길게 잡음 | ✅ |
| NFR-R3 | | **외부 의존성이 없어도 앱이 뜬다** | 크롤러·ai-mix·비전·지오코딩·푸시·노래인식은 키/사이드카가 없으면 해당 기능만 NoOp으로 꺼진다 | ✅ |
| NFR-R4 | | 콘텐츠 확보 실패가 저장 자체를 실패로 만들지 않는다 | 트랙 A/B 분리 → `PARTIAL` | ✅ |
| NFR-S1 | | 모든 아이템 접근은 워크스페이스 멤버십으로 인가한다 | `@AuthenticatedUser` + `WorkspaceMember` 검증 | ✅ |
| NFR-S2 | | 시크릿을 저장소에 커밋하지 않는다 | `.env.example`에 키 **이름만**. Mattermost OAuth 토큰은 DB 암호화 저장 | ✅ |
| NFR-S3 | | 외부에서 운영 메트릭·관리 엔드포인트에 접근할 수 없다 | nginx가 `/actuator` 전체 차단, 노출은 `health`·`prometheus`만 | ✅ |
| NFR-S4 | | 탈퇴 후 로그인 식별자를 보관하지 않는다 | 이메일→`@woojuin.invalid`, `provider_id`→NULL (V12) | ✅ |
| NFR-O1 | | 스키마 변경 이력을 추적하고, 엔티티와 어긋난 채로 배포되지 않는다 | Flyway + `ddl-auto: validate` (어긋나면 기동 실패) | ✅ |
| NFR-O2 | | DB 커넥션 고갈을 예방·진단할 수 있다 | 프로세서 트랜잭션 분리, `leak-detection-threshold`, HikariCP 메트릭 노출 | ✅ |
| NFR-O3 | | 배포는 변경된 파트만 빌드하고, 브랜치가 대상 환경을 정한다 | Jenkins Multibranch — `develop`→dev, `main`→prod, 경로 변경 감지 | ✅ |
| NFR-O4 | | 애플리케이션·컨테이너 로그와 메트릭을 한곳에서 본다 | Prometheus + Grafana + Loki + Promtail | ✅ |
| NFR-U1 | | 무거운 화면(3D·지도)이 첫 로딩을 느리게 하지 않는다 | 라우트 단위 lazy 분할 — 랜딩·로그인에 three/maplibre가 딸려오지 않는다 | ✅ |
| NFR-U2 | | 앱을 껐다 켜도 로그인 사용자가 랜딩으로 떨어지지 않는다 | PWA `start_url`(`/`)에 `GuestOnly` 가드 | ✅ |
| NFR-U3 | | 청크를 못 받아 생긴 에러 화면이 또 청크를 필요로 하지 않는다 | `ErrorPage`·`NotFoundPage`는 lazy로 두지 않음 | ✅ |

---

## 범위에서 제외한 것

기술 선택을 되돌리지 않기 위해 **의도적으로 배제한** 항목입니다.

| 항목 | 이유 |
| --- | --- |
| Next.js / SSR | 팀 결정 완료. 웹앱은 Vite SPA + PWA |
| Elasticsearch | 3주 일정에서 운영 부담이 크다. 검색은 부분 인덱스 + pgvector로 성립 |
| 카카오맵 · 구글맵 SDK | 지도는 OpenFreeMap + MapLibre(타일 무료·키 없음). 카카오는 **로컬 REST API(주소↔좌표)만** |
| 유튜브 자막 API | oEmbed 통합 방식으로 대체 (노션 FR-026은 폐기 방향) |
| 무료 노래 인식 경로(비공식 Shazam) | EC2 차단 가능성·약관 문제. AudD 정식 API 사용 |
| 프론트 localStorage 직접 사용 | Jotai / TanStack Query로 통일 |
| `items`의 GENERATED 컬럼 | 두 번째 기동부터 앱이 뜨지 않는다([ERD 참고](ERD.md#️-items에-generated-컬럼을-추가하지-마세요)) |
| Blue-Green 배포 | v1은 컨테이너 재생성(다운타임 ~30초). nginx 전환 설계 후 도입 |

---

## 관련 문서

- [기획서](PLANNING.md) — 이 요구사항이 나온 배경
- [API 명세서](API.md) — 각 요구사항의 실제 엔드포인트
- [ERD](ERD.md) — 요구사항을 지탱하는 데이터 모델
