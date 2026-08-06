# Wear OS 앱

손목에서 우주인에 저장하는 Wear OS 앱 (S15P11C105-458). Kotlin + Compose for Wear OS,
독립 실행(standalone) — 워치가 REST API 를 직접 호출하고 폰 동반 앱은 없다.

## 기능 단계 (기획서 2-7)

**앞 단계가 미완료면 다음 단계로 넘어가지 않습니다.**

1. **위치 저장** — 워치 버튼 한 번으로 현재 위치 저장 (FusedLocationProviderClient) — FR-053 ← **현재 범위**
2. AI 질문·답변 저장 — 음성 질문 → STT → OpenAI 질의 → 답변 저장 — FR-054 (화면만, fake)
3. 노래 인식 — 음악 인식 API 연동 — FR-055 (화면만, fake)

지금 상태: 전 화면 UI 는 fake repository 위에서 동작하는 데모다. -458 에서
로그인(링크 코드)과 위치 저장만 실서버로 연결한다 — 진행 상황은 지라 티켓 참고.

## 로그인 — 링크 코드

워치에 키보드가 없으므로 워치는 6자리 코드를 화면에 띄우기만 한다:

1. 워치: `POST /api/auth/device-link` → 코드 표시, `POST /api/auth/device-link/poll` 폴링
2. 웹: 마이페이지 → 연결된 기기 → **워치 연결** 에 코드 입력 (승인)
3. 워치: poll 이 APPROVED 와 함께 토큰을 받는다 — 세션은 다른 기기와 동일(-459)

토큰 정책: 통신 실패·타임아웃·5xx 에는 토큰을 폐기하지 않는다. 서버가 거부한
상태 코드(400·401·403)를 받은 때만 지운다 — 웹과 같은 계약(-455).

## 빌드·실행

- Android Studio 로 이 폴더(`wearos/`)를 연다 — 루트 모노레포가 아니라 이 폴더다.
  첫 실행 시 `local.properties`(SDK 경로)를 Android Studio 가 만들어 준다
- 명령줄 빌드: `./gradlew assembleDebug` (Windows 는 `gradlew.bat`)
- 실기기 설치: 워치의 개발자 옵션에서 무선 디버깅 켜기 →
  `adb pair <ip>:<port>` → `adb connect <ip>:<port>` → `./gradlew installDebug`
- minSdk 30 (Wear OS 3+)

### 콜드 스타트가 느려 보이면 빌드 종류를 먼저 보라

갤럭시 워치 실기기 실측(`adb shell am start -W`, 3회):

| 빌드 | 첫 프레임 | TotalTime |
| --- | --- | --- |
| debug | 5.5~6.3초 | 6.1~7.3초 |
| release(최적화 켬) | 1.2~1.4초 | 1.5~1.7초 |

같은 코드다. 차이는 R8·클래스 로딩이라 **디버그 빌드의 6초는 고칠 대상이 아니다** —
데모·발표는 릴리스 빌드로 한다. 앱 코드에서 줄일 수 있는 몫(서비스 생성·토큰 읽기)은
이미 걷어냈다: `Application.onCreate` → `setContent` 구간이 1.28초에서 0.65초가 됐다.

참고로 `release` 는 지금 `optimization { enable = false }` 이고 서명 설정이 없다.
켜서 재보니 위 표의 4배 차이가 났고 시작·홈·음성 진입은 정상이었지만, 타일·컴플리케이션
같은 나머지 경로는 확인하지 않았다 — 켜려면 그 검증부터 하고 켠다.

## 구조

- `presentation/` — Compose 화면·내비게이션·테마
- `domain/` — 모델·repository 인터페이스 (화면은 인터페이스만 안다)
- `data/fake/` — 데모용 fake 구현. 실서버 연결이 이 자리를 하나씩 대체한다
