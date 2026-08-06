# Chrome 익스텐션 연동 및 운영 안내

## 운영 정보

| 구분 | 값 |
| --- | --- |
| 개발 웹사이트 | `https://dev.woojuin.store` |
| 개발 백엔드 API | `https://api.dev.woojuin.store/api` |
| 웹사이트 | `https://woojuin.store` |
| 백엔드 API | `https://api.woojuin.store/api` |
| Chrome 웹스토어 익스텐션 ID | `aifkmpjpjedlamliamnloliencimdfco` |
| 익스텐션 출처 | `chrome-extension://aifkmpjpjedlamliamnloliencimdfco` |

익스텐션은 빌드 모드에 따라 호출 대상이 달라집니다.

| 명령 | 모드 | API | 웹앱 |
| --- | --- | --- | --- |
| `npm run build:local` | development | `http://localhost:8080/api` | `http://localhost:5173` |
| `npm run build:demo` | demo | `https://api.dev.woojuin.store/api` | `https://dev.woojuin.store` |
| `npm run build` | production | `https://api.woojuin.store/api` | `https://woojuin.store` |

**익스텐션 ID는 어떤 빌드에서도 같습니다.** `manifest.json`에 웹스토어 항목의 공개키(`key`)를
넣어 두어 압축해제 설치본도 스토어와 같은 ID를 씁니다. 그래서 서버에 등록할 출처는 하나뿐이고,
팀원마다 다른 ID를 추가할 필요가 없습니다. 이 키는 항목 생성 시 스토어가 만든 값이라 패키지를
새로 올려도 바뀌지 않습니다.

`host_permissions`에는 그 빌드가 실제로 호출하는 두 곳만 남습니다(`vite.config.ts`의
`narrowHostPermissions`). 배포판에 개발 주소가 남으면 웹스토어 심사에서 사유를 되묻고, 호스트
권한은 이미 상세 검토 대상이라 게시가 지연됩니다.

## 로그인 방식

익스텐션에는 **자체 로그인 폼이 없습니다.** 웹앱 로그인 화면을 그대로 쓰고 그 세션을
물려받습니다.

- 웹앱이 제공하는 로그인 수단을 익스텐션에서 **전부 다시 구현해야 합니다**(`LoginForm.tsx` 기준
  현재 구글 OAuth와 이메일·비밀번호 두 가지). 수단이 늘거나 줄 때마다 양쪽을 맞춰야 합니다.
- 익스텐션 안에서 OAuth를 직접 하려면(`chrome.identity`) 백엔드가 익스텐션용 `redirect_uri`를
  받아 주고 구글 콘솔에도 등록해야 합니다.
- 세션을 물려받으면 위 변경이 전부 불필요하고, 웹앱이 어떤 수단을 더하든 그대로 따라갑니다.
  그래서 팝업 버튼도 수단을 적지 않고 `우주인에서 로그인`으로 둡니다 — 이 버튼이 하는 일은
  우주인 로그인 화면을 여는 것이고, 어떤 수단을 쓸지는 그 화면이 정합니다.

동작 순서:

1. 팝업의 `우주인에서 로그인`을 누르면 먼저 **열려 있는 우주인 탭에서 세션을 찾습니다.**
   찾으면 창을 띄우지 않고 바로 로그인됩니다.
2. 없으면 460×760 크기의 작은 창으로 웹앱 로그인 화면을 엽니다.
3. 로그인이 끝나면 백엔드가 `/oauth/callback?accessToken=...&refreshToken=...`로 리다이렉트하고,
   익스텐션이 그 주소에서 토큰을 받아 저장한 뒤 **창을 자동으로 닫습니다.**
4. 팝업은 저장소 변화를 감지해 스스로 로그인 상태로 바뀝니다. 별도 알림은 띄우지 않습니다.

토큰은 액세스 토큰을 `chrome.storage.session`, 리프레시 토큰을 `chrome.storage.local`에 둡니다.
액세스 토큰이 만료되면(1시간) 401을 받고 자동으로 갱신하므로 사용자는 알아채지 못합니다.
로그아웃되는 경우는 리프레시 토큰 만료(14일), 웹에서의 로그아웃 또는 재로그인(리프레시 토큰이
사용자당 하나라 새 로그인이 이전 것을 밀어냅니다), 회원 탈퇴, 팝업의 로그아웃 버튼입니다.
팝업의 로그아웃은 익스텐션 토큰만 지우며 브라우저의 웹앱 세션은 건드리지 않습니다.

## 백엔드 변경사항

Chrome 익스텐션과 웹 프론트엔드가 함께 API를 호출할 수 있도록 CORS 설정을 복수 출처 방식으로
변경했습니다.

### `SecurityConfig.java`

`woojuin.cors.allowed-origins` 값을 쉼표로 나눠 여러 출처를 등록합니다. 앞뒤 공백과 빈 값,
중복 출처는 제거합니다.

허용 목록은 이 설정 하나만 봅니다 — 코드가 `http://localhost:5173`을 따로 더하지 않습니다.
더하면 운영에서도 개발 주소가 항상 허용되어, 사용자 PC의 5173에서 도는 아무 프로세스가 운영
API를 인증된 상태로 호출할 수 있습니다(`allowCredentials`도 켜져 있습니다).

### `application.yml`

```yaml
allowed-origins: ${CORS_ALLOWED_ORIGINS:${CORS_ALLOWED_ORIGIN:http://localhost:5173}}
```

`CORS_ALLOWED_ORIGINS`를 우선 사용하며 기존 `CORS_ALLOWED_ORIGIN`도 fallback으로 유지합니다.
변수를 주지 않으면 로컬 기본값이 5173을 넣어 줍니다. **변수를 직접 줄 때는 웹앱 주소도 반드시
목록에 포함해야 합니다.**

인증, 워크스페이스, 아이템 저장 API와 DB 구조는 익스텐션 연동을 위해 변경하지 않았습니다.

## CORS가 필요한 이유

크롬 익스텐션은 브라우저 차원에서는 CORS 제약을 받지 않습니다. `host_permissions`가 있으면
익스텐션 서비스워커와 익스텐션 페이지(팝업)는 CORS 헤더 없이도 응답을 읽습니다
([Chrome 문서](https://developer.chrome.com/docs/extensions/develop/concepts/network-requests)).
그럼에도 서버에 출처를 등록해야 하는 이유는 **막는 주체가 브라우저가 아니라 우리 서버**이기
때문입니다.

- 크롬은 익스텐션에서도 **POST에는 `Origin: chrome-extension://<id>` 헤더를 보냅니다**(GET에는
  보내지 않습니다).
- Spring의 `CorsFilter`는 허용 목록에 없는 Origin을 **403 `Invalid CORS request`로 잘라냅니다.**
  인증 필터보다 앞이라 토큰과 무관하게 막힙니다.

그래서 등록이 빠져 있으면 **워크스페이스 목록 조회(GET)는 되는데 저장(POST)만 403**이라는
헷갈리는 증상이 됩니다. 2026-08-02 운영에서 실측한 결과입니다.

```
Origin 없음                     → 401 (인증 단계까지 도달)
Origin: chrome-extension://...  → 403 Invalid CORS request
Origin: https://woojuin.store   → 401 (인증 단계까지 도달)
```

## 서버 설정

각 환경의 백엔드 환경변수에 웹사이트와 익스텐션 출처를 함께 등록합니다.

```dotenv
# 개발 서버
CORS_ALLOWED_ORIGINS=https://dev.woojuin.store,chrome-extension://aifkmpjpjedlamliamnloliencimdfco

# 운영
CORS_ALLOWED_ORIGINS=https://woojuin.store,chrome-extension://aifkmpjpjedlamliamnloliencimdfco
```

기존 환경변수에 다른 허용 출처가 있다면 삭제하지 말고 쉼표로 이어서 추가합니다. 환경변수 적용
후에는 백엔드를 재배포하거나 재시작해야 합니다.

적용 후 서버 내부에서 백엔드 상태를 확인합니다.

```bash
curl http://127.0.0.1:8080/actuator/health
```

외부 `https://api.woojuin.store/actuator/health`는 Nginx 정책에 따라 차단되므로 운영 상태 판단에
사용하지 않습니다.

CORS 확인은 실제 증상과 같은 **POST**로 하는 것이 확실합니다. 인증 없이 보내므로 401이면 CORS를
통과한 것이고, 403이면 출처 등록이 빠진 것입니다.

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST \
  'https://api.woojuin.store/api/workspaces/1/items' \
  -H 'Origin: chrome-extension://aifkmpjpjedlamliamnloliencimdfco' \
  -H 'Content-Type: application/json' \
  -d '{"type":"MEMO","content":"probe"}'
```

## 로컬 실행

익스텐션이 로컬 백엔드를 호출하므로 백엔드와 프론트엔드를 함께 띄웁니다. 프론트엔드는 구글
로그인 화면 때문에 필요합니다.

```powershell
# 백엔드 — CORS 목록에 웹앱 주소와 익스텐션 출처를 함께 넘깁니다
cd backend
.\gradlew.bat bootRun --args='--woojuin.cors.allowed-origins=http://localhost:5173,chrome-extension://aifkmpjpjedlamliamnloliencimdfco'
```

```powershell
# 프론트엔드
cd frontend
npm.cmd run dev
```

```powershell
# 익스텐션
cd extension
npm.cmd install
npm.cmd run build:local
```

Chrome에서:

1. `chrome://extensions` 접속
2. 개발자 모드 활성화
3. `압축해제된 확장 프로그램을 로드합니다` 선택
4. `extension\dist` 선택 — ID는 `aifkmpjpjedlamliamnloliencimdfco`로 고정입니다
5. 코드를 고쳤으면 다시 빌드한 뒤 카드의 새로고침(↻)을 누릅니다

변경을 계속 감지하려면 `npm.cmd run dev`를 사용합니다(빌드만 자동이며 새로고침은 수동입니다).

## 개발 서버 시연

시연하는 컴퓨터에서 프론트엔드와 백엔드를 실행할 필요가 없습니다. 익스텐션만 개발 서버용으로
빌드하면 팀 개발 서버에 로그인하고 저장할 수 있습니다. 개발 백엔드에 위 `서버 설정`의 개발 서버
값이 적용되어 있어야 합니다.

```powershell
cd extension
npm.cmd install
npm.cmd run build:demo
```

`chrome://extensions`에서 기존 압축해제 설치본을 새로고침하거나, 새로 설치한다면
`extension\dist`를 선택합니다.

## 운영 빌드 및 웹스토어 전달

```powershell
cd extension
npm.cmd install
npm.cmd run build
```

업로드 전에 번들에 운영 주소만 들어갔는지 확인합니다.

```powershell
Select-String -Path .\dist\assets\*.js, .\dist\background.js -Pattern 'woojuin\.store' |
  Select-Object -ExpandProperty Line -First 1
```

웹스토어 업로드용 ZIP 생성:

```powershell
cd extension
Compress-Archive `
  -Path .\dist\* `
  -DestinationPath .\woojuin-extension-production.zip `
  -Force
```

ZIP을 열었을 때 최상위에 `manifest.json`, `popup.html`, `background.js`, `icon128.png`,
`assets`, `fonts`가 보여야 합니다. `fonts`에는 팝업이 쓰는 Pretendard 서브셋과 라이선스
파일(SIL OFL 1.1)이 들어 있습니다. ZIP과 `dist`는 빌드 결과물이므로 Git에 커밋하지 않습니다.

Chrome 웹스토어에서는 ID가 `aifkmpjpjedlamliamnloliencimdfco`인 기존 우주인 항목의 새 버전으로
ZIP을 업로드합니다.

### 심사 시 권한 사유

호스트 권한이 포함되어 상세 검토 대상입니다. 대시보드의 `권한 요청 이유`에 다음 취지로
적습니다.

- `scripting` — 우주인 웹앱 탭에서 사용자의 로그인 세션을 읽어 오기 위해 사용합니다. 자체 로그인
  폼 없이 웹앱의 로그인 결과를 물려받으므로 인증 토큰만 조회하며, 다른 사이트나 페이지
  내용에는 접근하지 않습니다.
- 호스트 권한(`woojuin.store`, `api.woojuin.store`) — 저장 요청을 보낼 백엔드와 로그인 세션을
  확인할 웹앱입니다. 둘 다 본 확장 프로그램과 같은 서비스의 도메인입니다.
- `optional_host_permissions` — 사용자가 우클릭으로 이미지 저장을 선택한 순간에만 그 이미지가
  있는 사이트 권한을 요청해 파일을 내려받습니다. 사전에 부여받지 않습니다.

## 사용 및 확인 방법

익스텐션은 웹앱의 로그인 세션을 물려받습니다. 최초 실행 시 `우주인에서 로그인`을 누르면
됩니다(계정이 없어도 열린 화면에서 가입까지 이어집니다). 저장할 곳은 팝업의 선택기에서 고르며,
`Personal Space`와 `Workspaces`가 웹앱 사이드바와 같은 방식으로 구분됩니다.

### 현재 페이지 저장

1. 일반 HTTP/HTTPS 페이지 접속
2. 익스텐션 팝업 열기
3. `현재 페이지 저장` 선택
4. `우주인으로 보냈어요` 확인
5. `AI가 내용을 정리하고 있어요.` 안내 확인

### 텍스트 저장

1. 웹페이지 텍스트 선택
2. 우클릭
3. `선택한 텍스트를 우주인에 저장`
4. Chrome의 `우주인 저장 완료` 알림 확인

### 이미지 저장

1. 이미지에서 우클릭
2. `선택한 이미지를 우주인에 저장`
3. 최초 출처 권한 요청 허용
4. Chrome의 `우주인 저장 완료` 알림 확인

지원 이미지는 JPG, PNG, GIF, WEBP이며 최대 크기는 10MB입니다. 저장 결과는 웹사이트의 선택한
워크스페이스 라이브러리에서 확인합니다. 저장 요청은 즉시 접수되고 AI 제목·요약·카테고리 처리는
백그라운드에서 진행됩니다.

## 최종 검증

```powershell
cd extension
npm.cmd run build:local
npm.cmd run build
```

운영 빌드를 웹스토어에 올린 뒤 다음을 모두 확인합니다.

1. `우주인에서 로그인` — 작은 창이 뜨고, 끝나면 자동으로 닫히며 팝업이 로그인 상태로 바뀜
   (구글과 이메일·비밀번호 각각으로 확인)
2. 저장할 곳 선택기에 `Personal Space`와 `Workspaces`가 구분되어 표시됨
3. 현재 페이지 저장
4. 선택한 텍스트 우클릭 저장
5. 선택한 이미지 우클릭 저장
6. 웹 라이브러리에서 저장 및 AI 처리 결과 확인
