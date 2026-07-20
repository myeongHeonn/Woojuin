# 개발 컨벤션

## 브랜치 전략

```
main ── 배포 기준 브랜치 (보호 브랜치, 직접 푸시 금지)
  └── develop ── 통합 브랜치 (스프린트 개발분이 모이는 곳)
        ├── feature/FE-item-list         # 프론트 기능
        ├── feature/BE-item-save-api     # 백엔드 기능
        ├── feature/EXT-context-menu     # 익스텐션
        ├── fix/BE-summary-timeout       # 버그 수정
        └── chore/ci-cache               # 설정/잡무
```

- 브랜치 이름: `{타입}/{파트}-{짧은설명}` (파트: FE / BE / EXT / WEAR)
- 할일 관리는 지라(Jira)에서 진행
- `main`, `develop`은 보호 브랜치 설정 (Settings > Repository > Protected branches)

## 커밋 컨벤션 (Conventional Commits)

```
feat: 저장 항목 리스트뷰 카드 컴포넌트 추가
fix: OG 태그 파싱 시 인코딩 깨짐 수정
docs: README 로컬 실행 방법 갱신
refactor: ItemService에서 크롤링 로직 분리
test: 요약 폴백 3단계 단위 테스트 추가
chore: eslint 규칙 정리
```

- 타입: `feat` `fix` `docs` `refactor` `test` `chore` `style` `perf`
- 제목은 한글 OK, 명령형/현재형으로, 마침표 없이
- 본문이 필요하면 한 줄 띄우고 "왜" 위주로 작성
- AI 코딩 툴(Claude 등) 사용 시 `Co-Authored-By` 등 커밋 메시지에 툴 서명 남기지 않기

## MR (머지 리퀘스트)

- `feature/*` → `develop` 으로 MR, 리뷰어 1인 이상 승인 후 머지
- 셀프 머지 금지 (급한 hotfix는 MM에 공유 후 예외)
- MR 템플릿(.gitlab/merge_request_templates/Default.md) 사용
- 머지 방식: Squash 권장 (커밋 히스토리 깔끔하게)

## 코드 스타일

- **프론트**: ESLint + Prettier 설정 준수 (`npm run lint`)
- **백엔드**: Google Java Style 기반, IntelliJ 포매터 공유 예정
- API 응답은 공통 형식 준수: `{ "status": 200, "message": "success", "data": {} }`

## 환경변수

- 시크릿은 절대 커밋 금지 — `.env.example`에 키 이름만 추가하고 실제 값은 MM으로 공유
- CI/CD 시크릿은 GitLab Settings > CI/CD > Variables에 등록
