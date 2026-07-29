# Chrome 익스텐션 연동 안내

## 백엔드 변경사항

Chrome 익스텐션과 프론트엔드가 동시에 API를 호출할 수 있도록 CORS 설정을 복수 출처 방식으로 변경했습니다.

### `SecurityConfig.java`

기존에는 CORS 출처 하나만 받았습니다.

```java
@Value("${woojuin.cors.allowed-origin}")
private String allowedOrigin;
```

변경 후에는 쉼표로 구분된 복수 출처를 받아 등록합니다.

```java
@Value("${woojuin.cors.allowed-origins}")
private String allowedOriginsValue;
```

출처 문자열은 쉼표로 분리하고 공백, 빈 값, 중복을 제거합니다.

### `application.yml`

기존:

```yaml
allowed-origin: ${CORS_ALLOWED_ORIGIN:http://localhost:5173}
```

변경:

```yaml
allowed-origins: ${CORS_ALLOWED_ORIGINS:${CORS_ALLOWED_ORIGIN:http://localhost:5173}}
```

기존 `CORS_ALLOWED_ORIGIN`은 fallback으로 유지했습니다.

### `.env.example`

다음 예시를 추가했습니다.

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:5173,chrome-extension://YOUR_EXTENSION_ID
```

실제 로컬 `.env`에는 Chrome에서 확인한 익스텐션 ID를 입력합니다.

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:5173,chrome-extension://실제_익스텐션_ID
```

## 익스텐션 실행 방법

빌드:

```powershell
cd C:\S15P11C105-extension\extension
npm.cmd install
npm.cmd run build
```

Chrome에서:

1. `chrome://extensions` 접속
2. 개발자 모드 활성화
3. `압축해제된 확장 프로그램을 로드합니다` 선택
4. `extension/dist` 폴더 선택
5. 표시된 익스텐션 ID를 루트 `.env`에 등록
6. 백엔드 재시작

```powershell
cd C:\S15P11C105-extension\backend
.\gradlew.bat bootRun
```

## 사용 방법

최초 실행 시 익스텐션에서 별도로 로그인하고 저장할 워크스페이스를 선택합니다.

### 현재 페이지 저장

1. 익스텐션 팝업 열기
2. `현재 페이지 저장` 선택
3. `우주인으로 보냄 완료` 확인

### 텍스트 저장

1. 웹페이지 텍스트 선택
2. 우클릭
3. `선택한 텍스트를 우주인에 저장`

### 이미지 저장

1. 이미지에서 우클릭
2. `선택한 이미지를 우주인에 저장`
3. 최초 출처 권한 요청 허용

저장 성공 시 Chrome에 `우주인 저장 완료` 알림이 표시됩니다.

지원 이미지:

```text
JPG, PNG, GIF, WEBP
최대 10MB
```

저장 결과는 웹앱의 해당 워크스페이스 라이브러리에서 확인할 수 있습니다.

## 검증 명령

백엔드:

```powershell
cd C:\S15P11C105-extension\backend
.\gradlew.bat test
```

익스텐션:

```powershell
cd C:\S15P11C105-extension\extension
npm.cmd run build
```
