# 채팅 앱 연동 배포

## 사용자 이용 범위

### Mattermost

- `/woojuin` 커스텀 슬래시 명령은 **채널이 아니라 Mattermost 팀 단위**로 등록된다.
- 한 팀에 한 번 등록하면 그 팀의 공개·비공개 채널에서 별도 설치 없이 사용할 수 있다. 사용자는 각자 우주인 마이페이지에서 연결 코드를 발급해 최초 1회 계정을 연결한다.
- 다른 Mattermost 팀에서는 그 팀의 관리자가 슬래시 명령을 별도로 등록해야 한다.
- 현재 운영 설정은 `MATTERMOST_SLASH_TOKEN` 하나를 검증하므로 **하나의 Mattermost 팀 등록**을 지원한다. 여러 팀이 각각 만든 명령을 동시에 제공하려면 설치 토큰을 DB에서 관리하는 후속 기능이 필요하다.

Mattermost 슬래시 명령 설정값:

| 항목 | 운영 값 |
| --- | --- |
| 트리거 | `woojuin` |
| 요청 URL | `https://api.woojuin.store/api/integrations/mattermost/commands` |
| 요청 메서드 | `POST` |
| 응답 사용자명 | `woojuin` |
| 응답 아이콘 | `https://woojuin.store/icons/icon-512.png` |
| 자동완성 제안 | `우주인에 링크와 메모를 저장하고 저장한 정보를 검색합니다` |
| 자동완성 설명 | `[save <URL 또는 메모> | search <검색어> | workspace <list/번호> | connect <코드>]` |

명령을 저장한 뒤 Mattermost가 발급한 토큰을 운영 환경의 `MATTERMOST_SLASH_TOKEN`에 넣는다. 요청 URL에 OAuth callback URL을 넣으면 `405 Method Not Allowed`가 발생한다.

### Discord

- **사용자 설치**: 사용자가 우주인 마이페이지의 설치 버튼으로 자기 Discord 계정에 한 번 설치한다. 이후 Discord가 허용하는 서버·DM에서 슬래시 명령과 메시지의 `앱 → 우주인에 저장`을 사용할 수 있다.
- **서버 설치**: 서버 관리자가 서버에 한 번 설치하면 그 서버 구성원들이 사용할 수 있다. 채널마다 설치하지 않는다.
- 서버 정책에서 외부 앱 사용이 막혀 있으면 사용자 설치 앱은 해당 서버에서 보이지 않을 수 있다. 이때는 서버 관리자가 우주인을 서버에 설치해야 한다.
- 어느 설치 방식을 사용하든 각 우주인 사용자는 연결 코드를 통한 최초 1회 계정 연결이 필요하다.

## 배포 전 설정

### 백엔드 운영 환경변수

실제 값은 Jenkins의 `env-dev`/`env-prod` Secret file 또는 서버의 비공개 env 파일에만 둔다.

```dotenv
CHAT_BOT_SECRET=<32바이트 이상 랜덤값>
DISCORD_PUBLIC_KEY=<Discord Developer Portal Public Key>
DISCORD_APPLICATION_ID=<Discord Application ID>
MATTERMOST_SLASH_TOKEN=<Mattermost 명령 저장 후 발급된 토큰>
CHAT_INTEGRATION_FRONTEND_URL=https://woojuin.store
```

`DISCORD_BOT_TOKEN`은 백엔드 런타임이 아니라 명령 등록 스크립트에서만 사용한다.

### Discord Developer Portal

1. Installation에서 Guild Install과 User Install을 모두 활성화한다.
2. 기본 설치 범위에 `applications.commands`를 포함한다. 현재 기능은 Gateway 이벤트를 받지 않으므로 Privileged Gateway Intents는 켜지 않는다.
3. Interactions Endpoint URL을 `https://api.woojuin.store/api/integrations/discord/interactions`로 저장한다.
4. 운영 명령은 `DISCORD_GUILD_ID`를 비운 뒤 아래 스크립트로 **전역 등록**한다. Guild ID가 있으면 테스트 서버에서만 보인다.

```powershell
cd backend
.\scripts\register-discord-command.ps1
```

등록 스크립트는 명령을 Guild Install과 User Install 모두에서, 서버·봇 DM·개인 채널에서 사용할 수 있도록 등록한다. 전역 명령 반영에는 시간이 걸릴 수 있다.

> **앱 재설치 ≠ 명령 갱신.** Discord 앱을 서버/계정에 다시 설치해도 슬래시 명령 **스키마는 갱신되지 않는다.** 명령 구조를 바꿨다면(`discord-command.json` 수정) 반드시 위 등록 스크립트를 다시 실행해야 한다. 스크립트는 `PUT`(bulk overwrite)로 저장소 JSON을 명령 세트 전체로 덮어써서, 예전에 등록된 잔여 서브커맨드(예: 옛 `memo`, 그룹형 `workspace`)까지 함께 제거한다.

실제 Discord에 등록된 명령이 저장소 JSON과 일치하는지 확인하려면(읽기 전용, 토큰 미노출):

```powershell
cd backend
.\scripts\list-discord-command.ps1
```

`workspace` 변경은 Discord에서 위치 인자(`/woojuin workspace 3`)로 입력할 수 없다. 자동완성에서 `/woojuin workspace`를 고른 뒤 `number` 옵션에 번호를 넣어야 하며, 화면에는 `/woojuin workspace number:3`처럼 표시된다. Mattermost는 기존대로 `/woojuin workspace 3` 위치 인자를 지원한다.

## 배포 후 확인

1. Discord Developer Portal에서 운영 Interaction URL 검증 성공
2. Mattermost의 서로 다른 두 채널에서 `/woojuin help` 실행 성공
3. Discord 사용자 설치 후 `/woojuin help`와 메시지 우클릭 `앱 → 우주인에 저장` 노출
4. 두 플랫폼에서 연결 전 안내, 연결 코드 최초 1회 연결, URL·메모 저장 및 검색 확인
5. 저장 응답이 `PROCESSING`을 기다리지 않고 즉시 반환되고, 웹앱에서 이후 AI 처리 완료 확인
