# 프론트엔드 CI/배포용 이미지.
#
# 백엔드와 달리 **서버에서 돌 컨테이너를 만드는 게 아니다.** 프론트는 정적 파일이고
# 호스트 nginx 가 `/var/www/woojuin/{prod,dev}` 를 직접 서빙한다(결정 #18).
# 이 이미지는 "빌드 산출물을 담아 나르는 상자" 역할만 한다:
#
#   1) --target test  로 만들어 `docker run` 으로 lint/테스트 실행
#   2) 기본 타깃으로 만들면 /dist 에 빌드 결과만 담긴 얇은 이미지가 나온다
#   3) 그 이미지를 호스트 경로를 마운트한 컨테이너로 실행해 dist 를 꺼낸다
#      (Jenkins 는 컨테이너 안이라 호스트 /var/www 가 안 보이지만,
#       `docker run -v` 의 경로는 **호스트 데몬이 해석**하므로 호스트 경로가 유효하다)
#
#   docker build --target test -t woojuin-frontend-test:cache -f frontend/Dockerfile frontend
#   docker build --build-arg VITE_API_BASE_URL=... -t woojuin-frontend:<SHA> -f frontend/Dockerfile frontend

# ---------- 1단계: 의존성 + 테스트 환경 ----------
# ⚠️ node:20-alpine 을 쓸 수 없다. 프론트 테스트가 **실제 Chromium** 에서 돌기 때문이다
#    (vitest browser mode + Playwright — jsdom 은 CSS 레이아웃 엔진이 없어
#     getBoundingClientRect 가 전부 0 이라 레이아웃 검증이 불가능하다는 이유로 선택된 구성).
#    Playwright 는 glibc 를 요구하므로 musl 기반 alpine 에서 동작하지 않는다.
#    → 브라우저와 시스템 의존성(폰트 등)이 미리 깔린 공식 이미지를 쓴다.
#    이미지 태그의 버전은 package.json 의 playwright 버전과 **맞춰야** 한다.
FROM mcr.microsoft.com/playwright:v1.61.1-jammy AS test
WORKDIR /app

# 의존성 레이어 캐싱: 매니페스트만 먼저 넣는다.
# package-lock.json 이 안 바뀌면 아래 npm ci 는 캐시 히트.
COPY package.json package-lock.json ./
RUN npm ci

# 이미지에 브라우저가 이미 있지만, package.json 의 playwright 버전이 올라가
# 베이스 이미지와 어긋나면 "Executable doesn't exist" 가 난다. 그때 자동 보정되게 둔다.
# (버전이 맞으면 사실상 no-op)
RUN npx playwright install chromium

COPY . .

# ---------- 2단계: 빌드 ----------
FROM test AS build

# Vite 변수는 런타임이 아니라 **빌드 시점에 번들에 박힌다.**
# 그래서 dev/prod 는 각각 따로 빌드해야 하고, 같은 산출물을 두 곳에 복사할 수 없다.
#
# 📌 Vite 는 envDir 의 .env 파일뿐 아니라 **VITE_ 접두사가 붙은 실제 환경변수도 읽는다.**
#    vite.config.ts 의 `envDir: '..'`(레포 루트) 때문에 파일로 넘기려면 루트에 .env 를
#    만들어야 하는데, 환경변수로 주면 그 함정을 피할 수 있다.
ARG VITE_API_BASE_URL
ARG VITE_DISCORD_APPLICATION_ID
# Firebase 웹 앱 설정. dev/prod가 같은 Firebase 프로젝트를 쓰지만 값은 여기 하드코딩하지
# 않는다 — Jenkinsfile의 environment 블록 한 곳에서만 관리하고 --build-arg로 넘겨받는다
# (VITE_API_BASE_URL과 같은 패턴).
ARG VITE_FCM_API_KEY
ARG VITE_FCM_AUTH_DOMAIN
ARG VITE_FCM_PROJECT_ID
ARG VITE_FCM_STORAGE_BUCKET
ARG VITE_FCM_MESSAGING_SENDER_ID
ARG VITE_FCM_APP_ID
ARG VITE_FCM_VAPID_KEY
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_DISCORD_APPLICATION_ID=$VITE_DISCORD_APPLICATION_ID
ENV VITE_FCM_API_KEY=$VITE_FCM_API_KEY
ENV VITE_FCM_AUTH_DOMAIN=$VITE_FCM_AUTH_DOMAIN
ENV VITE_FCM_PROJECT_ID=$VITE_FCM_PROJECT_ID
ENV VITE_FCM_STORAGE_BUCKET=$VITE_FCM_STORAGE_BUCKET
ENV VITE_FCM_MESSAGING_SENDER_ID=$VITE_FCM_MESSAGING_SENDER_ID
ENV VITE_FCM_APP_ID=$VITE_FCM_APP_ID
ENV VITE_FCM_VAPID_KEY=$VITE_FCM_VAPID_KEY

# `npm run build` = `tsc -b && vite build` — 타입 체크가 이미 포함돼 있다.
# 그래서 별도 type-check 스테이지를 두지 않는다(중복).
RUN npm run build

# ---------- 3단계: 산출물만 ----------
# 최종 이미지는 dist 만 담아 얇게 유지한다(테스트용 브라우저가 딸려가지 않도록).
FROM alpine:3 AS dist
COPY --from=build /app/dist /dist
