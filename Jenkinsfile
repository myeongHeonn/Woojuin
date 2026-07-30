// 우주인 파이프라인 (백엔드 + 프론트엔드, dev/prod 겸용) — Multibranch Pipeline 전용
//
// 흐름: 대상 환경 계산 → 변경 경로 감지 → (변경된 파트만) Test → Build → Deploy → Health Check
//
// 배포 대상은 **브랜치가 정한다** (결정 #16: develop→dev, main→prod):
//   develop 브랜치 / develop 대상 MR → TARGET_ENV=dev   (MR 은 검증만)
//   main    브랜치 / main    대상 MR → TARGET_ENV=prod  (MR 은 검증만 — release MR)
//
// main 대상 MR 을 검증하는 이유 (develop 에서 이미 검증됐어도 중복이 아니다):
//   - 핫픽스로 main 이 develop 과 갈라져 있으면 머지 결과는 처음 보는 코드다
//   - 프론트는 prod 값(FE_API_BASE_URL)으로 **다시 빌드**되므로, prod 번들 자체가
//     이 MR 에서 처음 검증된다
//
//   - 백엔드: 해당 compose 스택의 backend 컨테이너 교체 (v1 = 단순 재생성, 다운타임 ~30초.
//     Blue-Green 은 결정 #9 의 다음 단계 — nginx 전환 메커니즘 설계 후 도입)
//   - 프론트: 호스트 nginx 가 서빙하는 /var/www/woojuin/{env} 에 정적 파일 배치
//
// ─────────────────────────────────────────────────────────────────────────────
// 이 파일은 **Multibranch Pipeline**(GitLab Branch Source) 잡에서 돈다.
//   - 브랜치·MR 을 플러그인이 자동 발견해 각각 잡을 만든다
//   - MR 잡은 **머지 결과**(타겟 브랜치에 머지했다고 가정한 코드)를 체크아웃한다
//     → "각자는 통과하는데 합치면 깨지는" 문제를 머지 전에 잡는다
//   - MR 여부는 `changeRequest()`, 브랜치는 `branch('develop')` 로 판별한다
//     (환경변수로는 MR 잡 = CHANGE_ID/CHANGE_TARGET, 브랜치 잡 = BRANCH_NAME)
//   - GitLab 커밋 상태 보고는 **플러그인이 자동으로** 한다 — 여기서 보내지 않는다
//
// 전제 (Jenkinsfile 만 봐서는 알 수 없는 것들):
//   - Jenkins 가 호스트 docker 를 조종할 수 있어야 한다(DooD, 결정 #22)
//     — docker CLI + **docker-buildx-plugin** + /var/run/docker.sock 마운트 + --group-add <GID>
//   - Multibranch 잡 설정: Discover branches 는 `develop` 만(Filter by name),
//     Discover merge requests 는 "Merging the MR with the current target branch revision"
//   - Lockable Resources 플러그인 (배포 스테이지의 lock() 이 사용)
//
// 🔀 동시성 설계 — 서로 다른 MR 은 잡이 달라 **동시에 돌 수 있다**:
//   - 빌드·테스트: **격리** — 이미지를 빌드마다 고유 태그(커밋 SHA)로 만들어 겹칠 것이 없다.
//     BuildKit 캐시는 태그와 무관한 별도 저장소라 태그를 지워도 캐시는 산다
//   - 배포: 각 환경(dev/prod)은 하나뿐이라 격리가 불가능 → **lock(woojuin-<env>-stack)** 으로 직렬화.
//     (지금은 develop 잡 + disableConcurrentBuilds 만으로도 직렬이지만, 잡이 늘거나 설정이
//     풀려도 안전하도록 자원 자체에 계약을 건다)
//
// ⚠️ DooD 경로 함정: Jenkins 는 컨테이너 안에 있지만 docker 명령은 **호스트 데몬**이 실행한다.
//    그래서 `docker run -v $WORKSPACE:/src` 는 데몬이 호스트에서 그 경로를 못 찾아
//    **빈 디렉토리를 마운트**한다(에러 없이 조용히 실패).
//    거꾸로 **호스트 경로는 유효**하므로, 프론트 배포는 그 성질을 이용한다(Deploy Frontend 참고).

pipeline {
    agent any

    options {
        // 빌드 이력이 무한정 쌓이면 jenkins-data 가 디스크를 먹는다.
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')

        // 🔴 같은 브랜치/MR 의 빌드를 직렬화한다.
        //    Multibranch 는 잡이 자동 생성돼 UI 체크박스를 손댈 수 없으므로 코드에 있어야 한다.
        disableConcurrentBuilds()
    }

    environment {
        // BuildKit 강제. docker-buildx-plugin 이 있으면 어차피 기본값이지만, 명시해 두면
        // 플러그인이 빠진 환경에서 **조용히 레거시 빌더로 폴백하는 대신 즉시 실패**한다.
        // (레거시 빌더는 캐시가 이미지 레이어에 얹혀 있어 아래 SHA 태그 + rmi 정리 방식이
        //  캐시를 파괴한다 — Backend Test 의 📜 역사 주석 참고)
        DOCKER_BUILDKIT   = '1'

        // COMPOSE_FILE / COMPOSE_PROJECT_NAME 은 docker compose 가 실제로 읽는 예약 변수명이라
        // 의도치 않은 동작을 피하려고 다른 이름을 쓴다.
        DEPLOY_FILE       = 'docker-compose.deploy.yml'

        // Firebase 웹 앱 설정. dev/prod가 같은 Firebase 프로젝트를 쓰므로 TARGET_ENV와
        // 무관하게 고정값이다 — 클라이언트 번들에 그대로 노출되는 공개값이라 여기 값을
        // 직접 둬도 안전하다(비밀값은 FCM_SERVICE_ACCOUNT_KEY_BASE64 하나뿐이고 그건
        // 백엔드 전용 Jenkins Secret file(env-dev/env-prod)에 있다).
        VITE_FCM_API_KEY             = 'AIzaSyDm3h_RP9ClzChLHYFJLDKvn85gtg1AEno'
        VITE_FCM_AUTH_DOMAIN         = 'woojuin-503006.firebaseapp.com'
        VITE_FCM_PROJECT_ID          = 'woojuin-503006'
        VITE_FCM_STORAGE_BUCKET      = 'woojuin-503006.firebasestorage.app'
        VITE_FCM_MESSAGING_SENDER_ID = '138341207339'
        VITE_FCM_APP_ID              = '1:138341207339:web:35f790c9ec6bdd07f3896e'
        VITE_FCM_VAPID_KEY           = 'BDZPcTlZsyHOvY1HSAVwHvXlcvi6d9Y0Dgkw9fEdYJusr4dxuLQNivp_4hKD4IJDdfJkY5UHpUVE4iwfxvmDzJk'

        // 환경별 값(STACK, FE_API_BASE_URL, 웹루트, credential, lock ...)은 여기 있지 않다 —
        // 브랜치/MR 타겟에 따라 달라지므로 Checkout 스테이지에서 계산한다(TARGET_ENV).
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    // 이미지 태그로 쓸 커밋 SHA 앞 7자리.
                    // latest 로만 태깅하면 "지금 뜬 게 어느 커밋인지" 추적이 불가능하고
                    // 롤백 대상도 지정할 수 없다(결정 #9).
                    env.SHORT_SHA = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()

                    // ── 대상 환경 계산 ─────────────────────────────────────────
                    // main 브랜치이거나 main 을 향하는 MR 이면 prod, 나머지는 dev.
                    // MR 도 대상 환경의 값으로 빌드해야 한다 — 특히 프론트는 FE_API_BASE_URL 이
                    // 번들에 박히므로, main 대상 MR 은 "prod 번들이 만들어지는가"를 검증한다.
                    def isProd = (env.BRANCH_NAME == 'main') || (env.CHANGE_TARGET == 'main')
                    env.TARGET_ENV        = isProd ? 'prod' : 'dev'
                    env.STACK             = "woojuin-${env.TARGET_ENV}"
                    env.BACKEND_CONTAINER = "${env.STACK}-backend-1"
                    env.FRONTEND_WEBROOT  = "/var/www/woojuin/${env.TARGET_ENV}"
                    env.ENV_CREDENTIAL    = "env-${env.TARGET_ENV}"                 // Jenkins Secret file
                    env.DEPLOY_LOCK       = "${env.STACK}-stack"                    // Lockable Resources
                    env.FRONT_HOST        = isProd ? 'woojuin.store' : 'dev.woojuin.store'
                    env.FE_API_BASE_URL   = isProd ? 'https://api.woojuin.store/api'
                                                   : 'https://api.dev.woojuin.store/api'

                    echo "빌드 대상 커밋: ${env.SHORT_SHA}"
                    echo "TARGET_ENV=${env.TARGET_ENV} / BRANCH_NAME=${env.BRANCH_NAME} / CHANGE_ID=${env.CHANGE_ID} / CHANGE_TARGET=${env.CHANGE_TARGET}"
                }
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    // 모노레포라서 프론트만 고쳐도 백엔드 테스트·빌드가 전부 돌면 낭비다
                    // (t2 CPU 크레딧 + 다른 빌드의 대기 시간). → 바뀐 파트만 돌린다.
                    //
                    // 📌 내장 `when { changeset }` 을 쓰지 않는 이유: changeset 은 빌드 changelog
                    //    기반이라 MR 에 커밋을 추가로 push 하면 **마지막 커밋의 변경만** 본다.
                    //    앞선 커밋이 backend 를 바꿨어도 스킵될 수 있다(검증 없이 초록불).
                    //    타겟 브랜치와의 명시적 diff 가 MR 검증에는 정확하다.

                    def base = ''
                    def baseWhy = ''

                    // 머지 커밋인지(두 번째 부모가 있는지) 판별.
                    def isMergeCommit = sh(
                        script: 'git rev-parse --verify --quiet HEAD^2 > /dev/null',
                        returnStatus: true
                    ) == 0

                    if (env.CHANGE_TARGET) {
                        // MR 잡 — 타겟 브랜치와 비교하면 "이 MR 이 가져오는 변경"이 나온다.
                        // 타겟 브랜치 ref 는 Branch Source 플러그인이 체크아웃 때 refspec 으로
                        // 항상 받아다 놓는다(+refs/heads/develop:refs/remotes/origin/develop)
                        // — 여기서 따로 fetch 하지 않는다. (예전에 방어용 fetch 를 뒀다가
                        // sh 에는 git 인증이 없어 매번 `fatal: could not read Username` 만 찍었다.
                        // 동작엔 무해했지만 로그의 가짜 fatal 이 디버깅을 헷갈리게 해서 제거)
                        base = "origin/${env.CHANGE_TARGET}"
                        baseWhy = "MR → 타겟 브랜치(${env.CHANGE_TARGET})"
                    } else if (isMergeCommit) {
                        // develop 의 머지 커밋 — 첫 번째 부모(= 머지 전 develop)와 비교하면
                        // "이 머지가 develop 에 가져온 변경"이 정확히 나온다.
                        // (GIT_PREVIOUS_SUCCESSFUL_COMMIT 보다 정확 — 그 값은 잡 단위라
                        //  직전 성공 빌드가 무엇이었느냐에 따라 무관한 변경까지 포함된다)
                        base = 'HEAD^1'
                        baseWhy = '머지 커밋 → 머지 전 develop(첫 번째 부모)'
                    } else if (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT) {
                        // 머지 커밋이 아닌 push(직접 push, squash 등).
                        // ⚠️ 여기서 HEAD^1 을 쓰면 커밋 여러 개를 한 번에 push 한 경우
                        //    마지막 커밋만 잡혀 앞선 변경을 놓친다. 넓게 잡히더라도 놓치지 않는 쪽.
                        base = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
                        baseWhy = '일반 push → 지난 성공 빌드(넓게, 안전하게)'
                    }

                    // 🔴 판단 불가 시 **전부 빌드**(fail-safe).
                    //    "변경 없음"으로 오해해 스킵하면 검증 없이 초록불이 뜨는 사고가 된다.
                    def buildAll = {
                        env.CHANGED_BE = 'true'
                        env.CHANGED_FE = 'true'
                    }

                    // 🔴 base 를 "정할 수 있었다"와 "그 base 가 의미 있다"는 다른 문제다.
                    //    base 가 HEAD 자신이면 diff 는 항상 비어 위와 같은 사고가 된다
                    //    (2026-07-28 실제 발생 — 삽질 기록 4).
                    if (base) {
                        def sameAsHead = sh(
                            script: "test \"\$(git rev-parse ${base})\" = \"\$(git rev-parse HEAD)\"",
                            returnStatus: true
                        ) == 0
                        if (sameAsHead) {
                            echo "⚠️ 비교 기준(${base})이 HEAD 와 같은 커밋 → 기준 무효 처리"
                            base = ''
                        }
                    }

                    if (!base) {
                        echo '비교 기준을 정할 수 없음(첫 빌드 등) → 전부 빌드'
                        buildAll()
                    } else {
                        // `A...HEAD`(세 점)는 공통 조상 기준 차이 — 기준 브랜치가 앞서 나가도
                        // "내가 바꾼 것"만 잡힌다.
                        def rc = sh(
                            script: "git diff --name-only ${base}...HEAD > .changed-files",
                            returnStatus: true
                        )
                        if (rc != 0) {
                            echo "⚠️ diff 실패 (base=${base}) → 안전하게 전부 빌드"
                            buildAll()
                        } else {
                            def files = readFile('.changed-files').trim()
                            echo "비교 기준: ${base}  (${baseWhy})\n변경된 파일:\n${files ?: '(없음)'}"
                            def lines = files ? files.split('\n') : []

                            // ai-mix(FastAPI 사이드카) 는 백엔드 **스택의 일부**다. 이미지를 커밋 SHA 로
                            // 고정해 배포하므로 배포 시점에 그 태그가 반드시 존재해야 한다 →
                            // ai-mix 만 바뀐 경우에도 스택을 다시 올려야 하고(새 이미지 반영),
                            // 백엔드만 바뀐 경우에도 ai-mix 이미지를 그 SHA 로 만들어 둬야 한다.
                            // 그래서 둘을 하나의 플래그로 묶는다. 캐시가 있으면 재빌드는 몇 초다.
                            env.CHANGED_BE = lines.any {
                                it.startsWith('backend/') || it.startsWith('ai/ai-mix/')
                                    || it.startsWith('crawler/')
                            } ? 'true' : 'false'
                            env.CHANGED_FE = lines.any { it.startsWith('frontend/') } ? 'true' : 'false'

                            // 파이프라인 자체나 배포 정의가 바뀌면 양쪽을 다 돌려 검증한다.
                            if (lines.any { it == 'Jenkinsfile' || it.startsWith('docker-compose') }) {
                                echo '파이프라인/배포 정의 변경 → 전부 빌드'
                                buildAll()
                            }
                        }
                    }

                    echo "백엔드 빌드: ${env.CHANGED_BE} / 프론트 빌드: ${env.CHANGED_FE}"
                }
            }
        }

        stage('Backend Test') {
            when { expression { env.CHANGED_BE == 'true' } }
            steps {
                // backend/Dockerfile 은 `gradle bootJar` 만 실행하고 **테스트를 돌리지 않는다.**
                // 그래서 이 스테이지가 없으면 테스트를 거치지 않은 이미지가 배포된다.
                //
                // 멀티스테이지의 build 스테이지(`FROM gradle:8.8-jdk21 AS build`)만 이미지로 만들어
                // 그 안에서 테스트한다 — 볼륨을 쓰지 않으므로 DooD 경로 함정을 피한다.
                // (`docker build` 자체는 CLI 가 컨텍스트를 데몬에 스트리밍하므로 경로 문제 없음)
                //
                // 태그가 **커밋 SHA(빌드별 고유)** 인 이유 — 동시에 도는 다른 MR 빌드와
                // 절대 겹치지 않는다(격리). BuildKit 의 캐시는 이미지 태그와 무관한 별도
                // 저장소에 살기 때문에, 스테이지 끝에서 이 이미지를 지워도 캐시는 유지된다.
                //
                // 📜 역사: 레거시 빌더 시절엔 캐시가 이미지 레이어에 얹혀 있어서 SHA 태그+rmi 가
                //    캐시를 파괴했고(bootJar 두 번, 빌드당 1분 낭비), 그 대응으로 고정 태그
                //    (:cache)를 썼다가 이번엔 **동시 빌드 경쟁**(빌드 A 가 B 의 코드를 테스트)이
                //    생겼다. BuildKit 전환으로 두 문제가 동시에 풀린다.
                sh """
                    docker build --target build \
                        -t woojuin-backend-test:${env.SHORT_SHA} \
                        -f backend/Dockerfile backend
                    docker run --rm woojuin-backend-test:${env.SHORT_SHA} \
                        gradle test --no-daemon
                """
                // TODO(t2 크레딧): gradle 캐시를 named volume 으로 유지하면 더 줄일 수 있다
                //   (`-v gradle-cache:/home/gradle/.gradle`). named volume 은 호스트 데몬이
                //   관리하므로 DooD 경로 함정과 무관하게 동작한다.
            }
            post {
                always {
                    // 테스트용 이미지는 이 스테이지에서만 쓰인다. BuildKit 캐시는 별도라
                    // 지워도 다음 빌드가 느려지지 않는다(레거시 빌더에선 금기였던 것).
                    sh "docker rmi woojuin-backend-test:${env.SHORT_SHA} || true"
                }
            }
        }

        stage('Backend Build Image') {
            when { expression { env.CHANGED_BE == 'true' } }
            steps {
                // 위 Test 가 통과한 커밋만 여기 온다.
                // Test 스테이지가 만든 build 스테이지 레이어를 그대로 재사용하므로
                // (Step 1~6 이 `Using cache` 로 지나가야 정상) 여기서는 실행 이미지만 얹힌다.
                sh """
                    docker build \
                        -t woojuin-backend:${env.SHORT_SHA} \
                        -f backend/Dockerfile backend
                """
            }
        }

        stage('AI Mix Build Image') {
            // ai-mix = 요약·카테고리 분류·임베딩·UMAP 3차원 축소를 담당하는 FastAPI 사이드카.
            // UMAP 이 파이썬 생태계에만 제대로 된 구현이 있어 별도 서비스로 뺐다(팀 결정).
            //
            // 크롤러와 같은 패턴: 레지스트리 없이 **서버에서 직접 빌드**하고 compose 는 image 로 참조.
            // 백엔드와 같은 이유로 태그는 커밋 SHA — 환경별 산출물이 아니라 런타임 env 주입이므로
            // 프론트처럼 -dev/-prod 접미사가 필요 없다(dev/prod 가 같은 이미지를 공유해도 무해).
            //
            // umap-learn 이 numpy/scikit-learn/numba 를 끌고 와 첫 빌드는 수 분이 걸린다.
            // 그 다음부터는 requirements 가 그대로면 BuildKit 캐시로 전부 CACHED 가 된다.
            when { expression { env.CHANGED_BE == 'true' } }
            steps {
                sh """
                    docker build \
                        -t woojuin-aimix:${env.SHORT_SHA} \
                        -f ai/ai-mix/Dockerfile ai/ai-mix
                """
            }
        }

        stage('Crawler Build Image') {
            // crawler = Scrapling(브라우저 렌더) 기반 본문 추출 폴백 사이드카.
            // Jsoup 으로 본문이 안 나오는 SPA·봇차단 페이지에서만 호출된다.
            //
            // ⚠️ **이미지가 2.29GB** 다(로컬 실측). camoufox 브라우저 바이너리 + GTK 계열
            //    시스템 라이브러리가 들어가서 ai-mix(922MB)의 2.5배다. 서버 디스크는 264G
            //    여유가 있어 문제없지만, SHA 태그가 쌓이는 건 레이어 공유라 커밋당 증가는 작다.
            //    첫 빌드는 apt + pip + `scrapling install`(브라우저 다운로드)로 수 분 걸린다.
            //
            // 📌 캐시된 재빌드가 느리면(2.29GB 재export) 태그 전략을 `:${TARGET_ENV}` 로 바꿔
            //    crawler/ 가 바뀔 때만 빌드하는 쪽을 검토할 것. 지금은 backend/aimix 와
            //    **같은 규칙(SHA 고정)** 을 유지해 "지금 뜬 게 어느 커밋인가"를 잃지 않는다.
            when { expression { env.CHANGED_BE == 'true' } }
            steps {
                sh """
                    docker build \
                        -t woojuin-crawler:${env.SHORT_SHA} \
                        -f crawler/Dockerfile crawler
                """
            }
        }

        stage('Frontend Test') {
            // 🔴 브라우저 테스트가 **끝났는데도 종료되지 않는** 사례를 겪었다(13분+ 매달림).
            //    전체 timeout(30분)에 맡기면 그만큼 잡이 점유된다. 여기서 빨리 실패하게 못 박는다.
            options { timeout(time: 10, unit: 'MINUTES') }
            when { expression { env.CHANGED_FE == 'true' } }
            steps {
                // 프론트 테스트는 **실제 Chromium** 에서 돈다(vitest browser mode + Playwright).
                // 그래서 alpine 을 못 쓰고 공식 Playwright 이미지를 베이스로 한다 — 상세는
                // frontend/Dockerfile 주석 참고. SHA 태그(격리) 이유는 Backend Test 와 동일.
                sh """
                    docker build --target test \
                        -t woojuin-frontend-test:${env.SHORT_SHA} \
                        -f frontend/Dockerfile frontend
                """

                // lint 와 test 를 **분리**한다 — 한 줄로 묶으면 멈췄을 때 어느 쪽인지 알 수 없다.
                sh "docker run --rm woojuin-frontend-test:${env.SHORT_SHA} npm run lint"

                // Chromium 을 컨테이너에서 돌릴 때 필요한 두 옵션:
                //   --init     : Chromium 은 자식 프로세스를 많이 띄운다. PID 1 이 좀비를
                //                수거하지 않으면 테스트가 끝나도 **컨테이너가 종료되지 않는다**
                //   --ipc=host : 기본 /dev/shm 은 64MB 뿐이라 Chromium 이 메모리 부족으로
                //                멈추거나 죽는다(Playwright 공식 문서 권고사항)
                sh "docker run --rm --init --ipc=host woojuin-frontend-test:${env.SHORT_SHA} npm test"

                // type-check 는 별도로 돌리지 않는다 — `npm run build` 가 `tsc -b && vite build`
                // 라서 다음 스테이지에서 이미 검증된다.
            }
            post {
                always {
                    sh "docker rmi woojuin-frontend-test:${env.SHORT_SHA} || true"
                }
            }
        }

        stage('Frontend Build') {
            when { expression { env.CHANGED_FE == 'true' } }
            steps {
                // VITE_* 는 런타임이 아니라 **빌드 시점에 번들에 박힌다** → 환경별로 다른 빌드다.
                // 그래서 태그에 SHA 만 쓰면 안 되고 **환경을 붙인다** — 같은 커밋이라도
                // dev 번들과 prod 번들은 내용이 다른 산출물이다(백엔드 이미지는 런타임 주입이라
                // SHA 만으로 충분한 것과 대조적).
                sh """
                    docker build \
                        --build-arg VITE_API_BASE_URL=${FE_API_BASE_URL} \
                        --build-arg VITE_FCM_API_KEY=${VITE_FCM_API_KEY} \
                        --build-arg VITE_FCM_AUTH_DOMAIN=${VITE_FCM_AUTH_DOMAIN} \
                        --build-arg VITE_FCM_PROJECT_ID=${VITE_FCM_PROJECT_ID} \
                        --build-arg VITE_FCM_STORAGE_BUCKET=${VITE_FCM_STORAGE_BUCKET} \
                        --build-arg VITE_FCM_MESSAGING_SENDER_ID=${VITE_FCM_MESSAGING_SENDER_ID} \
                        --build-arg VITE_FCM_APP_ID=${VITE_FCM_APP_ID} \
                        --build-arg VITE_FCM_VAPID_KEY=${VITE_FCM_VAPID_KEY} \
                        -t woojuin-frontend:${env.SHORT_SHA}-${env.TARGET_ENV} \
                        -f frontend/Dockerfile frontend
                """
                // 번들에 실제로 API 주소가 박혔는지 확인한다.
                // 값이 안 들어가도 **빌드는 성공**하고 `/api` 상대경로로 폴백하기 때문에
                // (client.ts 의 `?? '/api'`) 이 검사가 없으면 배포 후 프론트가 조용히 API 를 못 찾는다.
                sh """
                    docker run --rm woojuin-frontend:${env.SHORT_SHA}-${env.TARGET_ENV} \
                        sh -c 'grep -rqF "${FE_API_BASE_URL}" /dist/assets || (echo "번들에 API 주소가 없다 — VITE_API_BASE_URL 주입 실패"; exit 1)'
                """
            }
        }

        stage('Deploy Backend') {
            when {
                allOf {
                    // 배포 브랜치(develop→dev / main→prod)에서만. MR 잡은 branch 조건에서
                    // 걸러진다 (MR 잡의 BRANCH_NAME 은 MR-<번호> 형태).
                    anyOf { branch 'develop'; branch 'main' }
                    expression { env.CHANGED_BE == 'true' }
                }
            }
            steps {
                // 각 환경의 스택은 하나뿐인 공유 자원 — lock 으로 배포를 직렬화한다.
                // (같은 이름의 lock 을 쓰는 다른 빌드는 여기서 대기한다. 자원은 미리 등록할
                //  필요 없이 lock() 이 이름으로 자동 생성한다. dev/prod 는 락이 달라 서로
                //  안 기다린다 — 자원이 다르니까.)
                lock("${env.DEPLOY_LOCK}") {
                    // Jenkins 컨테이너는 호스트의 ~/woojuin/*/.env.* 를 볼 수 없다(마운트 안 함).
                    // 변수를 개별 Credential 로 10여 개 등록하는 대신 **파일 통째로 Secret file** 로 올린다.
                    // withCredentials 가 임시 파일에 풀어 주고, 빌드가 끝나면 삭제된다(로그에도 안 찍힘).
                    withCredentials([file(credentialsId: "${env.ENV_CREDENTIAL}", variable: 'ENV_FILE')]) {
                        // 이미지 태그를 쉘 환경변수로 준다. compose 치환에서 쉘 환경변수가
                        // --env-file 보다 우선하므로 .env 의 값을 이번 커밋 SHA 로 덮어쓴다.
                        //
                        // ── 어떤 사이드카를 띄우는가는 **.env 가 정한다** ──────────────────
                        // aimix·crawler 는 compose 에서 profile 뒤에 있어 기본 up 으로는 뜨지 않는다.
                        // 예전에는 여기 `--profile aimix` 를 박아 뒀는데, 그러면 환경별로 다르게
                        // 켤 수 없고 켜고 끄려면 파이프라인을 고쳐야 했다.
                        // `COMPOSE_PROFILES` 는 compose 예약 변수라 **--env-file 로도 먹는다**(실측).
                        //   .env.dev  : COMPOSE_PROFILES=aimix,crawler
                        //   .env.prod : COMPOSE_PROFILES=aimix       ← 이렇게 환경별로 다르게 가능
                        //
                        // ⚠️ 그 줄이 없으면 사이드카가 **하나도** 안 뜬다(그리고 이미 떠 있던 것은
                        //    desired state 에서 빠져 조용히 사라진다). 그래서 up 전에 실제 대상
                        //    서비스 목록을 로그로 남긴다 — 빠졌을 때 로그만 보고 알 수 있게.
                        sh """
                            echo "이번 배포 대상 서비스:"
                            docker compose -p ${STACK} \
                                --env-file "\$ENV_FILE" \
                                -f ${DEPLOY_FILE} \
                                config --services | sort | sed 's/^/  /'
                        """
                        sh """
                            BACKEND_IMAGE=woojuin-backend:${env.SHORT_SHA} \
                            AIMIX_IMAGE=woojuin-aimix:${env.SHORT_SHA} \
                            CRAWLER_IMAGE=woojuin-crawler:${env.SHORT_SHA} \
                            docker compose -p ${STACK} \
                                --env-file "\$ENV_FILE" \
                                -f ${DEPLOY_FILE} \
                                up -d
                        """
                    }
                }
            }
        }

        stage('Health Check') {
            when {
                allOf {
                    anyOf { branch 'develop'; branch 'main' }
                    expression { env.CHANGED_BE == 'true' }
                }
            }
            steps {
                // 호스트 포트(127.0.0.1:809x)로는 Jenkins 컨테이너에서 닿지 않고,
                // 외부 URL 은 nginx 가 /actuator 를 403 으로 막는다(의도된 설정).
                // → 컨테이너가 자기 자신을 찌르게 하면 네트워크 문제가 없다.
                //
                // 재시도하는 이유: 기동 직후에는 Spring 이 Tomcat 을 올리는 중이라 연결이 안 된다
                // (실측: `health: starting` 구간에 빈 응답, 4회째 통과). compose 의 start_period 와 같은 맥락.
                lock("${env.DEPLOY_LOCK}") {
                    sh """
                        for i in \$(seq 1 30); do
                            if docker exec ${BACKEND_CONTAINER} \
                                wget -qO- http://localhost:8080/actuator/health 2>/dev/null \
                                | grep -q '"status":"UP"'; then
                                echo "헬스체크 통과 (\${i}회 시도)"
                                exit 0
                            fi
                            echo "대기 중... (\${i}/30)"
                            sleep 5
                        done
                        echo "헬스체크 실패 — 2분 30초 안에 UP 이 되지 않았다"
                        # compose 로 로그를 보려면 --env-file 이 또 필요하고 \${VAR:?} 파싱을 다시 타므로
                        # 컨테이너 이름으로 직접 본다.
                        docker logs --tail=100 ${BACKEND_CONTAINER} || true
                        exit 1
                    """
                }
            }
        }

        stage('Sidecar Health Check') {
            // 사이드카(aimix·crawler)는 백엔드와 달리 **빌드를 실패시키지 않고 UNSTABLE 로만
            // 표시**한다.
            //   - 둘 다 compose 의 depends_on 에 없다(profile 서비스는 넣을 수 없다 — 넣으면
            //     compose 가 `depends on undefined service` 로 프로젝트 자체를 거부한다).
            //   - 백엔드가 사이드카 부재를 견딘다: aimix 없으면 요약·분류·임베딩이 NoOp,
            //     crawler 없으면 Jsoup 결과만 쓴다. 저장·검색·지도는 정상 동작한다.
            //     즉 이게 안 떠도 서비스는 살아 있으므로 배포를 막을 근거가 없다.
            //   - 그렇다고 조용히 넘기면 "그 기능만 안 되는" 상태를 아무도 모른다(우리가 반복해서
            //     겪은 실패 유형). 노란 빌드 + 로그로 드러나게 한다.
            //
            // 📌 켜지 않은 사이드카는 **컨테이너가 아예 없다**(.env 의 COMPOSE_PROFILES 가 결정).
            //    그건 정상 상태이므로 건너뛴다 — "안 켰다"와 "켰는데 죽었다"를 구분한다.
            when {
                allOf {
                    anyOf { branch 'develop'; branch 'main' }
                    expression { env.CHANGED_BE == 'true' }
                }
            }
            steps {
                script {
                    // ⚠️ `.each { }` 대신 for 루프를 쓴다 — Jenkins 는 파이프라인 코드를 CPS 로
                    //    변환하는데, 클로저 안의 `continue` 성격의 `return` 이 직관과 다르게
                    //    동작하는 사례가 알려져 있다. for 루프는 그 위험이 없다.
                    def sidecars = [
                        [name: 'aimix',   port: 8002, feature: '요약·분류·임베딩·우주 뷰'],
                        [name: 'crawler', port: 8001, feature: 'SPA·봇차단 페이지 본문 추출 폴백'],
                    ]
                    for (s in sidecars) {
                        def container = "${env.STACK}-${s.name}-1"

                        // healthcheck 의 start_period 가 30s → 최대 90초까지 기다린다.
                        def status = sh(returnStdout: true, script: """
                            for i in \$(seq 1 18); do
                                st=\$(docker inspect --format '{{.State.Health.Status}}' ${container} 2>/dev/null || echo missing)
                                if [ "\$st" != "starting" ]; then echo "\$st"; exit 0; fi
                                sleep 5
                            done
                            docker inspect --format '{{.State.Health.Status}}' ${container} 2>/dev/null || echo missing
                        """).trim()

                        if (status == 'missing') {
                            echo "${s.name}: 켜지 않음 (.env 의 COMPOSE_PROFILES 에 없다) — 건너뜀"
                            continue
                        }
                        if (status != 'healthy') {
                            sh "docker logs --tail=50 ${container} 2>&1 || true"
                            unstable("${s.name} 이 healthy 가 아니다(${status}) — ${s.feature} 가 " +
                                     '동작하지 않는다. 저장·검색·지도는 정상 동작한다.')
                            continue
                        }

                        // ⚠️ healthy 가 "동작함"을 뜻하지 않는다. ai-mix 의 `/health` 는 **API 키가
                        //    없어도 200 UP** 을 돌려준다(로컬 실측). 컨테이너 상태만 보면 키가 빠진
                        //    상태를 절대 못 잡는다 — "조용한 실패" 그대로다. 응답 본문까지 본다.
                        def body = sh(returnStdout: true, script: """
                            docker exec ${container} python -c "import urllib.request;print(urllib.request.urlopen('http://localhost:${s.port}/health').read().decode())" 2>/dev/null || echo '{}'
                        """).trim()
                        echo "${s.name} /health: ${body}"

                        // apiKeyConfigured 를 노출하는 사이드카(ai-mix)만 키 검사를 한다.
                        // crawler 는 외부 API 키가 필요 없어 healthy 면 그게 준비 완료다.
                        if (body.contains('"apiKeyConfigured":false')) {
                            unstable("${s.name} 은 떴지만 OPENROUTER_API_KEY 가 비어 있다 — ${s.feature} 가 " +
                                     '전부 실패한다(컨테이너는 healthy 로 보인다). 서버 .env 와 Jenkins ' +
                                     "credential(${env.ENV_CREDENTIAL}) 양쪽에 키를 넣을 것.")
                        } else {
                            echo "${s.name} 정상 (healthy)"
                        }
                    }
                }
            }
        }

        stage('Deploy Frontend') {
            when {
                allOf {
                    anyOf { branch 'develop'; branch 'main' }
                    expression { env.CHANGED_FE == 'true' }
                }
            }
            steps {
                // 프론트는 컨테이너로 띄우지 않는다 — 호스트 nginx 가 정적 파일을 직접 서빙한다.
                //
                // 📌 Jenkins 컨테이너에는 호스트의 /var/www 가 보이지 않는다. 그런데
                //    `docker run -v` 의 경로는 **호스트 데몬이 해석**하므로 호스트 경로가 유효하다.
                //    → 산출물이 담긴 이미지를 호스트 웹루트를 마운트한 채로 실행해 복사한다.
                //
                // ⚠️ 지우고 복사하는 사이 몇 초간 404 가 날 수 있다. 정적 파일의 무중단 교체는
                //    nginx root 를 심볼릭 링크로 두고 새 디렉토리를 만든 뒤 링크만 바꾸는 방식이다.
                //    dev 에는 과하다고 보고 지금은 단순하게 간다(개선 항목으로 문서화됨).
                //
                // `cp -r /dist/. /out/` 의 `.` 이 중요하다 — `/dist` 로 쓰면 /out/dist 가 되어
                // nginx 가 404 를 낸다.
                lock("${env.DEPLOY_LOCK}") {
                    sh """
                        docker run --rm \
                            -v ${FRONTEND_WEBROOT}:/out \
                            woojuin-frontend:${env.SHORT_SHA}-${env.TARGET_ENV} \
                            sh -c 'rm -rf /out/* && cp -r /dist/. /out/ && ls -1 /out | head'
                    """
                    // nginx 가 실제로 새 파일을 내주는지 확인한다 — 복사만 성공하고 nginx 설정이
                    // 어긋나 있으면 404 인데, 그건 배포 성공이 아니다.
                    // 방금 만든 이미지(alpine, busybox wget 내장)를 재사용한다.
                    // `--network host` 는 호스트 데몬이 해석하므로 호스트의 127.0.0.1:80 = nginx 에 닿는다.
                    // DNS 없이 vhost 를 고르려고 Host 헤더를 직접 준다.
                    sh """
                        docker run --rm --network host \
                            woojuin-frontend:${env.SHORT_SHA}-${env.TARGET_ENV} \
                            wget -q -O /dev/null --header='Host: ${env.FRONT_HOST}' http://127.0.0.1/ \
                            || (echo 'nginx 가 ${env.TARGET_ENV} 프론트를 서빙하지 않는다 — server 블록/root 경로 확인'; exit 1)
                        echo 'nginx 서빙 확인 완료 (${env.FRONT_HOST})'
                    """
                }
            }
        }
    }

    post {
        // GitLab 커밋 상태 보고는 GitLab Branch Source 가 **자동으로** 한다
        // (시작 시 running, 종료 시 success/failed/canceled). 여기서 보내면 중복이다.
        always {
            // ① dangling(태그 없는) 이미지 중 7일 넘은 것 정리.
            // ② BuildKit 빌드 캐시도 7일 넘은 것만 정리 — BuildKit 캐시는 이미지와 별도
            //    저장소라 따로 비워 줘야 디스크가 안 쌓인다. until 필터 덕에 최근 캐시는
            //    남아서 빌드 속도는 유지된다.
            // ⚠️ `docker system prune -a` 는 금지 — 태그 붙은 이미지까지 지워서
            //    롤백 대상 이미지와 jenkins-docker:lts 까지 날릴 수 있다.
            sh 'docker image prune -f --filter "until=168h" || true'
            sh 'docker builder prune -f --filter "until=168h" || true'
        }
    }
}
