# Chrome 익스텐션 연동 및 운영 안내

## 운영 정보

| 구분 | 값 |
| --- | --- |
| 개발 웹사이트 | `https://dev.woojuin.store` |
| 개발 백엔드 API | `https://dev.api.woojuin.store/api` |
| 웹사이트 | `https://woojuin.store` |
| 백엔드 API | `https://api.woojuin.store/api` |
| Chrome 웹스토어 익스텐션 ID | `aifkmpjpjedlamliamnloliencimdfco` |
| 운영 익스텐션 출처 | `chrome-extension://aifkmpjpjedlamliamnloliencimdfco` |

익스텐션은 빌드 모드에 따라 API 주소가 달라집니다.

- 로컬 빌드: `http://localhost:8080/api`
- 개발 서버 시연 빌드: `https://dev.api.woojuin.store/api`
- 운영 빌드: `https://api.woojuin.store/api`

## 백엔드 변경사항

Chrome 익스텐션과 웹 프론트엔드가 함께 API를 호출할 수 있도록 CORS 설정을 복수 출처 방식으로 변경했습니다.

### `SecurityConfig.java`

`woojuin.cors.allowed-origins` 값을 쉼표로 나눠 여러 출처를 등록합니다. 앞뒤 공백과 빈 값, 중복 출처는 제거합니다.

### `application.yml`

```yaml
allowed-origins: ${CORS_ALLOWED_ORIGINS:${CORS_ALLOWED_ORIGIN:http://localhost:5173}}
```

`CORS_ALLOWED_ORIGINS`를 우선 사용하며 기존 `CORS_ALLOWED_ORIGIN`도 fallback으로 유지합니다.

인증, 워크스페이스, 아이템 저장 API와 DB 구조는 익스텐션 연동을 위해 변경하지 않았습니다.

## 운영 서버 설정

운영 백엔드 환경변수에 웹사이트와 최종 익스텐션 출처를 함께 등록합니다.

```dotenv
CORS_ALLOWED_ORIGINS=https://woojuin.store,chrome-extension://aifkmpjpjedlamliamnloliencimdfco
```

기존 환경변수에 다른 허용 출처가 있다면 삭제하지 말고 쉼표로 이어서 추가합니다. 환경변수 적용 후 기존 배포 방식으로 백엔드를 재배포하거나 재시작해야 합니다.

환경변수 적용 후 서버 내부에서 백엔드 상태를 확인합니다. 배포 구조에 따라 컨테이너 내부 또는 백엔드 호스트에서 실행합니다.

```bash
curl http://127.0.0.1:8080/actuator/health
```

외부 `https://api.woojuin.store/actuator/health`는 Nginx 정책에 따라 차단될 수 있으므로 운영 상태 판단에 사용하지 않습니다.

백엔드 재배포·재시작 후 외부에서 운영 익스텐션 CORS를 확인합니다.

```powershell
$headers = @{
  Origin = 'chrome-extension://aifkmpjpjedlamliamnloliencimdfco'
  'Access-Control-Request-Method' = 'POST'
  'Access-Control-Request-Headers' = 'content-type'
}
Invoke-WebRequest `
  -Uri 'https://api.woojuin.store/api/auth/login' `
  -Method Options `
  -Headers $headers
```

응답 상태가 `200`이고 `Access-Control-Allow-Origin`이 운영 익스텐션 출처와 같아야 합니다.

## 개발 서버 시연

시연하는 컴퓨터에서 프론트엔드와 백엔드를 직접 실행할 필요가 없습니다. 익스텐션만 개발 서버용으로 빌드하면 팀 개발 서버에 로그인하고 저장할 수 있습니다.

개발 백엔드에는 시연에 사용할 익스텐션 출처를 허용해야 합니다. Chrome 웹스토어 설치본으로 시연하면 운영 익스텐션 ID를, `압축해제된 확장 프로그램`으로 시연하면 해당 컴퓨터의 로컬 익스텐션 ID를 사용합니다. 둘 다 사용할 예정이면 두 출처를 모두 등록합니다.

```dotenv
CORS_ALLOWED_ORIGINS=https://dev.woojuin.store,chrome-extension://aifkmpjpjedlamliamnloliencimdfco,chrome-extension://YOUR_LOCAL_EXTENSION_ID
```

현재 개발 컴퓨터의 로컬 익스텐션 ID가 `jlhbnpkcmoadekhnbmdihflbepmhjjhc`라면 다음과 같이 설정합니다.

```dotenv
CORS_ALLOWED_ORIGINS=https://dev.woojuin.store,chrome-extension://aifkmpjpjedlamliamnloliencimdfco,chrome-extension://jlhbnpkcmoadekhnbmdihflbepmhjjhc
```

시연용 빌드:

```powershell
cd C:\S15P11C105-extension\extension
npm.cmd install
npm.cmd run build:demo
```

Chrome에서 `chrome://extensions`를 열고 기존 압축해제 설치본을 새로고침합니다. 새로 설치한다면 `C:\S15P11C105-extension\extension\dist`를 선택합니다.

개발 서버 CORS 확인:

```powershell
$headers = @{
  Origin = 'chrome-extension://jlhbnpkcmoadekhnbmdihflbepmhjjhc'
  'Access-Control-Request-Method' = 'POST'
  'Access-Control-Request-Headers' = 'content-type'
}
Invoke-WebRequest `
  -Uri 'https://dev.api.woojuin.store/api/auth/login' `
  -Method Options `
  -Headers $headers
```

응답 상태가 `200`이고 `Access-Control-Allow-Origin`이 시연에 사용하는 익스텐션 출처와 같아야 합니다.

## 로컬 실행

```powershell
cd C:\S15P11C105-extension\extension
npm.cmd install
npm.cmd run build:local
```

Chrome에서:

1. `chrome://extensions` 접속
2. 개발자 모드 활성화
3. `압축해제된 확장 프로그램을 로드합니다` 선택
4. `C:\S15P11C105-extension\extension\dist` 선택
5. 로컬 익스텐션 ID를 로컬 백엔드의 `CORS_ALLOWED_ORIGINS`에 추가
6. 백엔드 재시작

로컬 개발 중 변경을 계속 감지하려면 다음 명령을 사용합니다.

```powershell
npm.cmd run dev
```

## 운영 빌드 및 웹스토어 전달

운영 빌드:

```powershell
cd C:\S15P11C105-extension\extension
npm.cmd install
npm.cmd run build
```

`npm.cmd run build`로 만들어진 `dist`는 운영 API를 사용합니다.

웹스토어 업로드용 ZIP 생성:

```powershell
cd C:\S15P11C105-extension\extension
Compress-Archive `
  -Path .\dist\* `
  -DestinationPath .\woojuin-extension-production.zip `
  -Force
```

ZIP을 열었을 때 최상위에 `manifest.json`, `popup.html`, `background.js`, `icon128.png`, `assets`가 보여야 합니다. ZIP과 `dist`는 빌드 결과물이므로 Git에 커밋하지 않습니다.

Chrome 웹스토어에서는 ID가 `aifkmpjpjedlamliamnloliencimdfco`인 기존 우주인 항목의 새 버전으로 ZIP을 업로드합니다.

## 사용 및 확인 방법

웹 로그인 상태와 익스텐션 로그인 상태는 공유되지 않습니다. 익스텐션 최초 실행 시 우주인 이메일·비밀번호로 별도 로그인하고 저장할 워크스페이스를 선택합니다.

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

지원 이미지는 JPG, PNG, GIF, WEBP이며 최대 크기는 10MB입니다. 저장 결과는 웹사이트의 선택한 워크스페이스 라이브러리에서 확인합니다. 저장 요청은 즉시 접수되고 AI 제목·요약·카테고리 처리는 백그라운드에서 진행됩니다.

## 최종 검증

```powershell
cd C:\S15P11C105-extension\extension
npm.cmd run build:local
npm.cmd run build
```

운영 빌드를 웹스토어에 올린 뒤 다음 기능을 모두 확인합니다.

1. 익스텐션 이메일 로그인
2. 워크스페이스 목록 조회
3. 현재 페이지 저장
4. 선택한 텍스트 우클릭 저장
5. 선택한 이미지 우클릭 저장
6. 웹 라이브러리에서 저장 및 AI 처리 결과 확인
